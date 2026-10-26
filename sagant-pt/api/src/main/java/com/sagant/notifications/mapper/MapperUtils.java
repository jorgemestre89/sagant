package com.sagant.notifications.mapper;

import jakarta.annotation.PostConstruct;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class MapperUtils {

    private final ModelMapper modelMapper;
    private static ModelMapper modelMapperStatic;

    public MapperUtils(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    @PostConstruct
    protected void initModelMapperStatic(){
        modelMapperStatic = modelMapper;
    }

    /**
     * para mapeo de objetos
     *
     * @param <C> clase a la que se desea mapear
     * @param destinationType objeto.class al que se desea mapear
     * @param <O> objeto que se desea mapear
     * @param source objeto de tipo O que se desea ser mapear
     *
     * @return  objeto mapeado de tipo <C>
     *
     * */
    public static  <C, O> C mapObject(O source, Class<C> destinationType) {
        return modelMapperStatic.map(source, destinationType);
    }

    /**
     * para mapeo de collecciones
     *
     * @param <C> clase a la que se desea mapear
     * @param destinationType objeto.class al que se desea mapear
     * @param <O> objeto que se desea mapear
     * @param source objeto de tipo O que se desea ser mapear
     *
     * @return  collecion de objetos mapeados de tipo <C>
     *
     * */
    public static  <C, O> List<C> mapList(List<O> source, Class<C> destinationType) {
        return source.stream()
                .map(element -> modelMapperStatic.map(element, destinationType))
                .toList();
    }
}
