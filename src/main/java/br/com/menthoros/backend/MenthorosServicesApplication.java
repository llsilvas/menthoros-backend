package br.com.menthoros.backend;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class MenthorosServicesApplication {

    public static void main(String[] args) {
        log.info(":: Iniciando Menthoros ::");
        long startTime = System.currentTimeMillis(); // Captura o tempo de início

        SpringApplication.run(MenthorosServicesApplication.class, args);
        long endTime = System.currentTimeMillis(); // Captura o tempo de fim
        long totalTime = endTime - startTime; // Calcula o tempo total em milissegundos
        log.info(":: Menthoros iniciado com sucesso :: - " + totalTime + " s" );

    }

}
