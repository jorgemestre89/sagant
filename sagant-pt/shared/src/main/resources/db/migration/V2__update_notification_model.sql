-- Renombrar el contenido de la notificación.
ALTER TABLE notifications
    RENAME COLUMN message TO body;

-- Ajustar las longitudes según el modelo actualizado.
ALTER TABLE notifications
ALTER COLUMN recipient TYPE VARCHAR(255),
    ALTER COLUMN body TYPE VARCHAR(5000);

-- Incorporar el canal y la prioridad.
-- Se agregan inicialmente con valores por defecto para
-- mantener la compatibilidad con registros existentes.
ALTER TABLE notifications
    ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'LOG',
    ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM';

-- El asunto es opcional.
ALTER TABLE notifications
    ADD COLUMN subject VARCHAR(255);

-- Metadatos opcionales almacenados como JSON.
ALTER TABLE notifications
    ADD COLUMN metadata JSONB;