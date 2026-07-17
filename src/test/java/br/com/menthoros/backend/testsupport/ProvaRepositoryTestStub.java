package br.com.menthoros.backend.testsupport;

import br.com.menthoros.backend.repository.ProvaRepository;

import java.lang.reflect.Proxy;
import java.util.Collections;

/**
 * Stub mínimo de {@link ProvaRepository} para testes de {@code TsbServiceImpl} que não exercitam
 * o fluxo de inferência de limiar por prova — {@code findProvasRealizadasRecentes} sempre retorna
 * lista vazia. Centralizado aqui para evitar divergência entre as classes de teste que o usam.
 */
public final class ProvaRepositoryTestStub {

    private ProvaRepositoryTestStub() {
    }

    public static ProvaRepository semProvas() {
        return (ProvaRepository) Proxy.newProxyInstance(
                ProvaRepository.class.getClassLoader(),
                new Class<?>[]{ProvaRepository.class},
                (proxy, method, args) -> {
                    if ("findProvasRealizadasRecentes".equals(method.getName())) return Collections.emptyList();
                    if ("toString".equals(method.getName())) return "ProvaRepositoryStub";
                    throw new UnsupportedOperationException("Método não suportado: " + method.getName());
                }
        );
    }
}
