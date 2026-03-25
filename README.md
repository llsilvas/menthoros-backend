
# Menthoros Services

O Menthoros Services é uma aplicação Spring Boot projetada para gerenciar atletas e seus planos de treinamento. Ele utiliza tecnologias modernas como Spring AI para recursos de inteligência artificial, PostgreSQL com pgvector para armazenamento de vetores e Flyway para migrações de banco de dados.

## Visão Geral da Arquitetura

O projeto segue uma arquitetura em camadas padrão, com controladores para expor os endpoints da API, serviços para a lógica de negócios, repositórios para acesso a dados e DTOs para transferência de dados.

### Tecnologias Utilizadas

- **Java 24**: A versão mais recente do Java, garantindo acesso aos recursos e melhorias de desempenho mais recentes.
- **Spring Boot 3.5.4**: Fornece uma base robusta para a criação de aplicações autônomas e de nível de produção.
- **Spring Security OAuth2**: Integração com Keycloak para autenticação e autorização via JWT.
- **Keycloak**: Identity Provider (IdP) para gestão de usuários, autenticação e autorização com suporte a multi-tenancy.
- **Spring AI**: Facilita a integração de recursos de IA, como o processamento de linguagem natural com o OpenAI.
- **PostgreSQL com pgvector**: Permite o armazenamento e a consulta eficientes de embeddings de vetores, essenciais para recursos de IA.
- **Redis**: Cache distribuído para melhorar a performance da aplicação.
- **Flyway**: Gerencia as migrações de esquema de banco de dados, garantindo a consistência do banco de dados em diferentes ambientes.
- **MapStruct**: Simplifica o mapeamento entre DTOs e entidades, reduzindo o código boilerplate.
- **Lombok**: Reduz ainda mais o código boilerplate por meio de anotações.
- **Docker Compose**: Orquestra os serviços da aplicação, facilitando a configuração e a execução do ambiente de desenvolvimento.

## Primeiros Passos

### Pré-requisitos

- JDK 24
- Docker e Docker Compose
- Maven
- Uma chave de API da OpenAI

### Configuração

1. **Clone o repositório:**
   ```bash
   git clone https://github.com/seu-usuario/menthoros.git
   cd menthoros
   ```

2. **Configure as variáveis de ambiente:**
   Copie o arquivo `.env.example` para `.env` e configure as variáveis necessárias:
   ```bash
   cp .env.example .env
   ```

   Edite o arquivo `.env` e configure pelo menos:
   ```
   OPENAI_API_KEY=sua_chave_de_api_da_openai
   KC_ADMIN_USER=admin
   KC_ADMIN_PASSWORD=admin123
   ```

3. **Inicie os serviços com Docker Compose:**
   ```bash
   docker-compose up -d
   ```

   Isso irá iniciar:
   - PostgreSQL (com pgvector) na porta 5432
   - Keycloak na porta 8443 (admin console) e 9000 (metrics)
   - Redis na porta 6379

   **Nota**: O Keycloak pode levar até 2 minutos para estar completamente pronto na primeira inicialização.

4. **Execute a aplicação:**
   ```bash
   ./mvnw spring-boot:run
   ```

A aplicação estará disponível em `http://localhost:8098`.

## Configuração do Keycloak

O Menthoros utiliza Keycloak para autenticação e autorização com suporte a multi-tenancy. Após iniciar os serviços, você precisará configurar o Keycloak:

### Acesso ao Admin Console

1. Acesse o Keycloak Admin Console: `http://localhost:8443`
2. Faça login com as credenciais configuradas no `.env`:
   - Username: `admin` (valor de `KC_ADMIN_USER`)
   - Password: `admin123` (valor de `KC_ADMIN_PASSWORD`)

### Configuração Inicial do Realm

Siga o guia completo de configuração em: `docs/MULTI_TENANCY_INTEGRATION_GUIDE.md`

Principais passos:

1. **Criar Realm**: Crie um realm chamado `menthoros-app`
2. **Configurar Client**: Crie um client OAuth2 para a aplicação
3. **Definir Roles**: Configure as roles (ADMIN, TECNICO, VISUALIZADOR)
4. **Criar Token Mappers**: Configure mappers para incluir `tenant_id` no JWT
5. **Criar Groups**: Crie grupos para representar cada assessoria (tenant)
6. **Criar Usuários**: Adicione usuários e associe-os aos grupos

### URLs Importantes

- **Admin Console**: http://localhost:8443/admin
- **Realm Endpoint**: http://localhost:8443/realms/menthoros-app
- **Health Check**: http://localhost:9000/health
- **Metrics**: http://localhost:9000/metrics

## Endpoints da API

A aplicação expõe os seguintes endpoints da API REST:

- `POST /atleta`: Cadastra um novo atleta.
- `POST /api/planos/gerar/{atletaId}`: Gera um novo plano de treino para um atleta.
- `POST /api/treinos`: Cria um novo registro de treino realizado.

## Estrutura do Projeto

A estrutura do projeto está organizada da seguinte forma:

- `src/main/java/com/menthoros`: Contém o código-fonte principal da aplicação.
  - `config`: Classes de configuração do Spring.
  - `controller`: Controladores da API REST.
  - `converter`: Conversores de tipo de dados.
  - `dto`: Objetos de Transferência de Dados (DTOs) para entrada e saída da API.
  - `entity`: Entidades JPA que representam as tabelas do banco de dados.
  - `enums`: Enumerações utilizadas no projeto.
  - `mapper`: Mapeadores MapStruct para conversão entre DTOs e entidades.
  - `repository`: Repositórios Spring Data JPA para acesso ao banco de dados.
  - `services`: Lógica de negócios da aplicação.
- `src/main/resources`: Contém os arquivos de configuração da aplicação.
  - `application.yml`: Arquivo de configuração principal do Spring Boot.
  - `db/migration`: Scripts de migração do Flyway.
- `src/test/java`: Contém os testes da aplicação.

## Contribuição

Contribuições são bem-vindas! Sinta-se à vontade para abrir uma issue ou enviar um pull request.
