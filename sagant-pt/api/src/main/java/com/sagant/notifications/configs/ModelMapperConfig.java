package com.sagant.notifications.configs;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.modelmapper.config.Configuration.AccessLevel;

@Configuration
public class ModelMapperConfig {
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();

        //definimos el mapeo de campos privados cuando los nombres y tipos son compatibles
        mapper.getConfiguration()
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(AccessLevel.PRIVATE);

        return mapper;
    }
}
