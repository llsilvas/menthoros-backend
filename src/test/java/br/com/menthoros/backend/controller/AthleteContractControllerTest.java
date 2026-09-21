package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.ClockConfig;
import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.input.AthleteContractInputDto;
import br.com.menthoros.backend.entity.AthleteContract;
import br.com.menthoros.backend.entity.AthleteInvoice;
import br.com.menthoros.backend.enums.ContractPeriodicity;
import br.com.menthoros.backend.enums.InvoiceStatus;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.AthleteContractMapper;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.AthleteContractService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static br.com.menthoros.backend.testsupport.JwtTestSupport.adminJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.proprietarioJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.stubUsuarioAtivo;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.tecnicoJwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP do proprietário (task 3.1): autorização real (CA11), 404 vindo do serviço (CA12),
 * validação do input e 409 nas transições. O serviço é mock; o mapper e o Clock são reais.
 */
@WebMvcTest(AthleteContractController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class, AthleteContractMapper.class, ClockConfig.class})
class AthleteContractControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AthleteContractService contractService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UsuarioSyncService usuarioSyncService;
    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private TenantValidationRepository tenantValidationRepository;

    private final UUID athleteId = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        stubUsuarioAtivo(usuarioSyncService);
    }

    @Nested
    @DisplayName("autorização (CA11)")
    class Autorizacao {

        @Test
        @DisplayName("TECNICO sem PROPRIETARIO → 403 em todos os endpoints, sem tocar o serviço")
        void tecnicoBarrado() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/{id}/contrato", athleteId).with(tecnicoJwt()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/v1/atletas/{id}/contrato", athleteId).with(tecnicoJwt()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(inputValido())))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/atletas/{id}/contrato/encerrar", athleteId).with(tecnicoJwt()).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/mensalidades/{id}/baixa", invoiceId).with(tecnicoJwt()).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/mensalidades/{id}/baixa", invoiceId).with(tecnicoJwt()).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/mensalidades/{id}/cancelar", invoiceId).with(tecnicoJwt()).with(csrf()))
                    .andExpect(status().isForbidden());

            verify(contractService, never()).findActiveContract(any());
            verify(contractService, never()).createOrUpdate(any(), any());
            verify(contractService, never()).end(any());
            verify(contractService, never()).markPaid(any(), any(), any());
            verify(contractService, never()).undoPayment(any());
            verify(contractService, never()).cancel(any());
        }

        @Test
        @DisplayName("PROPRIETARIO e ADMIN → 200")
        void proprietarioEAdmin() throws Exception {
            when(contractService.findActiveContract(athleteId)).thenReturn(Optional.of(contrato()));
            when(contractService.listInvoices(any())).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/atletas/{id}/contrato", athleteId).with(proprietarioJwt()))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/atletas/{id}/contrato", athleteId).with(adminJwt()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("sem token → 401")
        void semToken() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/{id}/contrato", athleteId))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /atletas/{id}/contrato")
    class GetContrato {

        @Test
        @DisplayName("contrato com mensalidades, mais recente primeiro, com valor e flag de vencida")
        void contratoComMensalidades() throws Exception {
            AthleteContract contract = contrato();
            when(contractService.findActiveContract(athleteId)).thenReturn(Optional.of(contract));
            when(contractService.listInvoices(contract.getId())).thenReturn(List.of(
                    mensalidade(LocalDate.now().plusDays(20), InvoiceStatus.OPEN),
                    mensalidade(LocalDate.now().minusDays(10), InvoiceStatus.OPEN)));

            mockMvc.perform(get("/api/v1/atletas/{id}/contrato", athleteId).with(proprietarioJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(contract.getId().toString()))
                    .andExpect(jsonPath("$.periodicity").value("MONTHLY"))
                    .andExpect(jsonPath("$.amount").value(250.00))
                    .andExpect(jsonPath("$.active").value(true))
                    .andExpect(jsonPath("$.invoices.length()").value(2))
                    .andExpect(jsonPath("$.invoices[0].overdue").value(false))
                    .andExpect(jsonPath("$.invoices[1].overdue").value(true))
                    .andExpect(jsonPath("$.invoices[1].amount").value(250.00));
        }

        @Test
        @DisplayName("sem contrato ativo → 404")
        void semContrato() throws Exception {
            when(contractService.findActiveContract(athleteId)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/atletas/{id}/contrato", athleteId).with(proprietarioJwt()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /atletas/{id}/contrato")
    class PutContrato {

        @Test
        @DisplayName("input válido → 200 com o contrato")
        void upsert() throws Exception {
            AthleteContract contract = contrato();
            when(contractService.createOrUpdate(eq(athleteId), any())).thenReturn(contract);
            when(contractService.listInvoices(contract.getId())).thenReturn(List.of());

            mockMvc.perform(put("/api/v1/atletas/{id}/contrato", athleteId).with(proprietarioJwt()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(inputValido())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.athleteId").value(athleteId.toString()))
                    .andExpect(jsonPath("$.dueDay").value(10));
        }

        @Test
        @DisplayName("dueDay 0 ou 32 → 400 sem chamar o serviço")
        void diaInvalido() throws Exception {
            for (int dia : new int[]{0, 32}) {
                var input = new AthleteContractInputDto(ContractPeriodicity.MONTHLY, new BigDecimal("250.00"), dia, LocalDate.now(), true);
                mockMvc.perform(put("/api/v1/atletas/{id}/contrato", athleteId).with(proprietarioJwt()).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(input)))
                        .andExpect(status().isBadRequest());
            }
            verify(contractService, never()).createOrUpdate(any(), any());
        }

        @Test
        @DisplayName("atleta de outro tenant → 404 (CA12)")
        void outroTenant() throws Exception {
            when(contractService.createOrUpdate(eq(athleteId), any())).thenThrow(new DomainNotFoundException("Atleta não encontrado"));

            mockMvc.perform(put("/api/v1/atletas/{id}/contrato", athleteId).with(proprietarioJwt()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(inputValido())))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("mensalidades")
    class Mensalidades {

        @Test
        @DisplayName("baixa sem corpo → 200 PAID com defaults do serviço")
        void baixaSemCorpo() throws Exception {
            AthleteInvoice paga = mensalidade(LocalDate.now().plusDays(5), InvoiceStatus.PAID);
            paga.setPaidAt(LocalDate.now());
            paga.setPaidAmount(new BigDecimal("250.00"));
            when(contractService.markPaid(invoiceId, null, null)).thenReturn(paga);

            mockMvc.perform(post("/api/v1/mensalidades/{id}/baixa", invoiceId).with(proprietarioJwt()).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAID"))
                    .andExpect(jsonPath("$.paidAmount").value(250.00));
        }

        @Test
        @DisplayName("baixa com corpo repassa data e valor")
        void baixaComCorpo() throws Exception {
            when(contractService.markPaid(invoiceId, LocalDate.of(2026, 10, 8), new BigDecimal("180.00")))
                    .thenReturn(mensalidade(LocalDate.now(), InvoiceStatus.PAID));

            mockMvc.perform(post("/api/v1/mensalidades/{id}/baixa", invoiceId).with(proprietarioJwt()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"paidAt\":\"2026-10-08\",\"paidAmount\":180.00}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("mensalidade de outro tenant → 404 (CA12)")
        void baixaOutroTenant() throws Exception {
            when(contractService.markPaid(eq(invoiceId), any(), any())).thenThrow(new DomainNotFoundException("Mensalidade não encontrada"));

            mockMvc.perform(post("/api/v1/mensalidades/{id}/baixa", invoiceId).with(proprietarioJwt()).with(csrf()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("cancelar mensalidade paga → 409 (CA8)")
        void cancelarPaga() throws Exception {
            when(contractService.cancel(invoiceId)).thenThrow(new DomainConflictException("Não é possível cancelar de mensalidade PAID"));

            mockMvc.perform(post("/api/v1/mensalidades/{id}/cancelar", invoiceId).with(proprietarioJwt()).with(csrf()))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("desfazer baixa → 200 OPEN")
        void desfazer() throws Exception {
            when(contractService.undoPayment(invoiceId)).thenReturn(mensalidade(LocalDate.now(), InvoiceStatus.OPEN));

            mockMvc.perform(delete("/api/v1/mensalidades/{id}/baixa", invoiceId).with(proprietarioJwt()).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OPEN"));
        }
    }

    // ---- helpers ----

    private AthleteContract contrato() {
        return AthleteContract.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).athleteId(athleteId)
                .periodicity(ContractPeriodicity.MONTHLY).amount(new BigDecimal("250.00"))
                .dueDay(10).startDate(LocalDate.of(2026, 9, 21)).build();
    }

    private AthleteInvoice mensalidade(LocalDate dueDate, InvoiceStatus status) {
        return AthleteInvoice.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).contractId(UUID.randomUUID())
                .dueDate(dueDate).amount(new BigDecimal("250.00")).status(status).build();
    }

    private static AthleteContractInputDto inputValido() {
        return new AthleteContractInputDto(ContractPeriodicity.MONTHLY, new BigDecimal("250.00"), 10, LocalDate.of(2026, 9, 21), true);
    }
}
