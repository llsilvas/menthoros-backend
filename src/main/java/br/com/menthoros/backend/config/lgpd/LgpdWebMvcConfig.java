package br.com.menthoros.backend.config.lgpd;

import br.com.menthoros.backend.repository.UsuarioLgpdConsentRepository;
import br.com.menthoros.backend.security.LgpdConsentInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra o {@link LgpdConsentInterceptor} para todas as rotas.
 *
 * <p>O interceptor é global de propósito: um endpoint de escrita novo nasce coberto pelo gate, sem
 * ninguém precisar lembrar de incluí-lo numa lista. Enumerar rotas protegidas falharia em silêncio
 * no dia em que alguém esquecesse — modo de falha inaceitável num controle de conformidade. As
 * isenções vivem dentro do interceptor, ao lado das guardas que as justificam.
 *
 * <p><b>Por que o configurer é um {@code @Bean} e não a própria classe:</b> {@code @WebMvcTest}
 * inclui na fatia todo {@code @Configuration} que implementa {@link WebMvcConfigurer}, mas não
 * inclui {@code @Component} comum — então uma classe-configurer arrastaria o interceptor e suas
 * dependências (properties e repositório) para dentro de 22 slices que não têm nenhuma delas,
 * derrubando o contexto. Pelo mesmo motivo o interceptor é criado aqui como {@code @Bean} em vez de
 * ser {@code @Component}: {@link org.springframework.web.servlet.HandlerInterceptor} também está na
 * lista de inclusão automática da fatia. Assim tudo sai do slice de uma vez, sem
 * {@code excludeFilters} espalhado por 22 testes, e a injeção por construtor segue falhando rápido
 * em produção se uma dependência sumir.
 */
@Configuration
public class LgpdWebMvcConfig {

    @Bean
    LgpdConsentInterceptor lgpdConsentInterceptor(LgpdProperties lgpdProperties,
                                                 UsuarioLgpdConsentRepository consentRepository) {
        return new LgpdConsentInterceptor(lgpdProperties, consentRepository);
    }

    @Bean
    WebMvcConfigurer lgpdConsentInterceptorConfigurer(LgpdConsentInterceptor lgpdConsentInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(lgpdConsentInterceptor).addPathPatterns("/**");
            }
        };
    }
}
