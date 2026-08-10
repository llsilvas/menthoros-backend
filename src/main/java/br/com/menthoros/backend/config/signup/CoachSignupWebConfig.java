package br.com.menthoros.backend.config.signup;

import br.com.menthoros.backend.security.PublicRequestSizeLimitFilter;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registro dos componentes web do auto-cadastro.
 *
 * <p>Existe para manter o {@link PublicRequestSizeLimitFilter} fora do escaneamento por
 * {@code @Component}: o slice do {@code @WebMvcTest} inclui todo {@code Filter} do contexto, e um
 * filtro com dependência de bean quebra todos os slices de uma vez. Declarado como {@code @Bean}
 * numa {@code @Configuration}, o slice o ignora — e ele é exercitado onde pertence, no seu próprio
 * teste unitário.</p>
 */
@Configuration
public class CoachSignupWebConfig {

    @Bean
    FilterRegistrationBean<PublicRequestSizeLimitFilter> publicRequestSizeLimitFilter(
            CoachSignupProperties properties) {
        var registro = new FilterRegistrationBean<>(new PublicRequestSizeLimitFilter(properties));
        registro.addUrlPatterns("/api/public/*");
        // Antes do rate limit: recusar um corpo gigante nao deve consumir cota do contador.
        registro.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 2);
        return registro;
    }
}
