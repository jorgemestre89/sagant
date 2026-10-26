package com.sagant.notifications.dispatchers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.domain.enums.NotificationChannel;
import com.sagant.notifications.domain.enums.NotificationPriority;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceNotificationDispatcherTest {

    // Servidor HTTP local que simula el servicio externo al que se envían las notificaciones.
    private HttpServer server;

    // Dispatcher que se está probando.
    private ServiceNotificationDispatcher dispatcher;

    // Guarda el método HTTP recibido por el servidor simulado.
    private final AtomicReference<String> requestMethod = new AtomicReference<>();

    // Guarda la ruta a la que se envió la petición HTTP.
    private final AtomicReference<String> requestPath = new AtomicReference<>();

    // Guarda el Content-Type enviado en la petición.
    private final AtomicReference<String> contentType = new AtomicReference<>();

    // Guarda el cuerpo JSON recibido para poder verificar sus datos.
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    // Permite configurar el código HTTP que devolverá el servidor simulado.
    private int responseStatus;

    // Permite interpretar el JSON recibido y comprobar sus campos.
    private final ObjectMapper objectMapper = new ObjectMapper();

    /*
     * Prepara el entorno de cada prueba.
     *
     * Crea un servidor HTTP local en un puerto disponible, configura
     * el endpoint /notifications para registrar la petición recibida
     * y responder con el código HTTP configurado.
     *
     * Finalmente, construye el dispatcher apuntando a ese servidor.
     * No necesita conectarse a un servicio externo real.
     */
    @BeforeEach
    void setUp() throws IOException {

        // Por defecto, el servidor simulado responde correctamente.
        responseStatus = 200;

        // El puerto 0 permite que el sistema operativo asigne uno disponible.
        server = HttpServer.create(
                new InetSocketAddress("localhost", 0),
                0
        );

        // Define el endpoint que recibirá las peticiones del dispatcher.
        server.createContext("/notifications", exchange -> {

            // Registra información de la petición recibida.
            requestMethod.set(exchange.getRequestMethod());
            requestPath.set(exchange.getRequestURI().getPath());

            contentType.set(
                    exchange.getRequestHeaders().getFirst("Content-Type")
            );

            // Lee y guarda el cuerpo JSON enviado por el dispatcher.
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            ));

            // Obtiene el código HTTP configurado para esta prueba.
            int statusCode = responseStatus;

            // Prepara una respuesta JSON sencilla para el cliente HTTP.
            byte[] response = "{\"result\":\"ok\"}"
                    .getBytes(StandardCharsets.UTF_8);

            // Envía el código HTTP y la longitud del cuerpo de respuesta.
            exchange.sendResponseHeaders(statusCode, response.length);

            // Escribe la respuesta y cierra el flujo de salida.
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });

        // Inicia el servidor local para que pueda recibir peticiones.
        server.start();

        // Construye la URL utilizando el puerto asignado al servidor.
        String serviceUrl = "http://localhost:"
                + server.getAddress().getPort()
                + "/notifications";

        // Crea el dispatcher con RestClient y la URL del servidor simulado.
        dispatcher = new ServiceNotificationDispatcher(
                RestClient.builder(),
                serviceUrl,
                objectMapper
        );
    }

    /*
     * Libera los recursos del servidor HTTP después de cada prueba.
     *
     * Evita dejar servidores locales activos entre pruebas
     * o mantener puertos ocupados innecesariamente.
     */
    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /*
     * Verifica que el dispatcher esté asociado al canal SERVICE.
     *
     * Este canal representa las notificaciones que deben enviarse
     * a un servicio externo mediante HTTP.
     */
    @Test
    void shouldIdentifyServiceChannel() {
        assertEquals(
                NotificationChannel.SERVICE,
                dispatcher.getChannel()
        );
    }

    /*
     * Verifica que el dispatcher envíe correctamente una notificación.
     *
     * Comprueba que:
     * - Se utilice el método HTTP POST.
     * - Se invoque la ruta /notifications.
     * - El Content-Type sea application/json.
     * - El JSON contenga recipient, subject y body con los valores esperados.
     * - No se envíen campos adicionales en el DTO.
     */
    @Test
    void shouldSendPostWithExpectedJson() throws Exception {

        // Crea una notificación de ejemplo para enviar al servicio.
        Notification notification = createNotification();

        // Configura una respuesta HTTP exitosa.
        configureResponseStatus(200);

        // Ejecuta el envío real mediante RestClient hacia el servidor local.
        dispatcher.dispatch(notification);

        // Verifica el método HTTP utilizado.
        assertEquals("POST", requestMethod.get());

        // Verifica el endpoint invocado.
        assertEquals("/notifications", requestPath.get());

        // Verifica que el contenido se haya enviado como JSON.
        assertTrue(contentType.get().startsWith("application/json"));

        // Convierte el cuerpo recibido a un árbol JSON para inspeccionarlo.
        JsonNode json = objectMapper.readTree(requestBody.get());

        // Comprueba que el JSON contiene los datos esperados.
        assertEquals(
                "destinatario@test.com",
                json.get("recipient").asText()
        );

        assertEquals(
                "Asunto de prueba",
                json.get("subject").asText()
        );

        assertEquals(
                "Contenido de prueba",
                json.get("body").asText()
        );

        // Verifica que el DTO no incluya campos adicionales.
        assertEquals(3, json.size());
    }

    /*
     * Verifica el comportamiento cuando el servicio externo devuelve HTTP 500.
     *
     * RestClient, al utilizar retrieve(), considera los códigos HTTP 4xx y 5xx
     * como errores y lanza una excepción. El dispatcher debe propagarla para
     * que la capa que gestiona los envíos pueda decidir cómo tratar el fallo.
     */
    @Test
    void shouldPropagateHttpError() {

        // Crea una notificación para probar el escenario de error.
        Notification notification = createNotification();

        // Configura el servidor simulado para responder con HTTP 500.
        configureResponseStatus(500);

        // Comprueba que el dispatcher propaga la excepción HTTP.
        assertThrows(
                RestClientResponseException.class,
                () -> dispatcher.dispatch(notification)
        );

        // Verifica que la petición llegó al servidor simulado.
        assertEquals("POST", requestMethod.get());
        assertEquals("/notifications", requestPath.get());
    }

    /**
     * Construye una notificación de prueba con datos conocidos.
     *
     * Centraliza los datos utilizados por las pruebas para evitar
     * repetir la construcción de la entidad en cada método.
     *
     * @return una notificación destinada al canal SERVICE.
     */
    private Notification createNotification() {
        return new Notification(
                "destinatario@test.com",
                NotificationChannel.SERVICE,
                "Asunto de prueba",
                "Contenido de prueba",
                NotificationPriority.HIGH,
                Map.of()
        );
    }

    /**
     * Configura el código HTTP que devolverá el servidor local.
     *
     * Permite simular respuestas exitosas, como HTTP 200,
     * o respuestas de error, como HTTP 500.
     *
     * @param statusCode código HTTP que debe devolver el servidor.
     */
    private void configureResponseStatus(int statusCode) {
        responseStatus = statusCode;
    }
}
