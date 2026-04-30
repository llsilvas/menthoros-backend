package br.com.menthoros.backend;

import org.springframework.boot.SpringApplication;

public class TestMenthorosServicesApplication {

    public static void main(String[] args) {
        SpringApplication.from(MenthorosServicesApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
