package com.portoajuda.aplicacao_osc.entity;

import com.portoajuda.aplicacao_osc.enums.OrigemDesativacao;
import com.portoajuda.aplicacao_osc.enums.StatusOsc;
import com.portoajuda.aplicacao_osc.enums.TipoEventoHistoricoOsc;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Getter
@Entity
@Immutable
@Table(name = "historico_status_osc", indexes = {
        @Index(name = "idx_historico_status_osc_osc_data", columnList = "id_osc_snapshot, data_criacao")
})
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class HistoricoStatusOsc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_osc", foreignKey = @ForeignKey(name = "fk_historico_status_osc_osc"))
    private Osc osc;

    @Column(name = "id_osc_snapshot", nullable = false)
    private Integer oscIdSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", length = 20)
    private StatusOsc statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false, length = 20)
    private StatusOsc statusNovo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 30)
    private TipoEventoHistoricoOsc tipoEvento;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_acao", nullable = false, length = 30)
    private OrigemDesativacao origemAcao;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_desativacao_anterior", length = 30)
    private OrigemDesativacao origemDesativacaoAnterior;

    @Column(name = "data_desativacao_anterior")
    private LocalDateTime dataDesativacaoAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_desativou_anterior",
            foreignKey = @ForeignKey(name = "fk_historico_status_osc_desativador_anterior"))
    private Usuario desativadorAnterior;

    @Column(name = "id_usuario_desativou_anterior_snapshot")
    private Integer desativadorAnteriorIdSnapshot;

    @Column(name = "motivo_desativacao_anterior", columnDefinition = "TEXT")
    private String motivoDesativacaoAnterior;

    @Column(name = "data_arquivamento_anterior")
    private LocalDateTime dataArquivamentoAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_arquivou_anterior",
            foreignKey = @ForeignKey(name = "fk_historico_status_osc_arquivador_anterior"))
    private Usuario arquivadorAnterior;

    @Column(name = "id_usuario_arquivou_anterior_snapshot")
    private Integer arquivadorAnteriorIdSnapshot;

    @Column(name = "motivo_arquivamento_anterior", columnDefinition = "TEXT")
    private String motivoArquivamentoAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", foreignKey = @ForeignKey(name = "fk_historico_status_osc_usuario"))
    private Usuario usuario;

    @Column(name = "id_usuario_snapshot")
    private Integer usuarioIdSnapshot;

    @Column(name = "autoridade_ator", nullable = false, length = 100)
    private String autoridadeAtor;

    @CreationTimestamp
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    public static HistoricoStatusOsc registrar(
            Osc osc,
            StatusOsc statusAnterior,
            StatusOsc statusNovo,
            OrigemDesativacao origemAcao,
            TipoEventoHistoricoOsc tipoEvento,
            OrigemDesativacao origemDesativacaoAnterior,
            LocalDateTime dataDesativacaoAnterior,
            Usuario desativadorAnterior,
            Integer desativadorAnteriorIdSnapshot,
            String motivoDesativacaoAnterior,
            LocalDateTime dataArquivamentoAnterior,
            Usuario arquivadorAnterior,
            Integer arquivadorAnteriorIdSnapshot,
            String motivoArquivamentoAnterior,
            Usuario usuario,
            String autoridadeAtor,
            String motivo) {
        HistoricoStatusOsc historico = new HistoricoStatusOsc();
        historico.osc = osc;
        historico.oscIdSnapshot = osc.getId();
        historico.statusAnterior = statusAnterior;
        historico.statusNovo = statusNovo;
        historico.origemAcao = origemAcao;
        historico.tipoEvento = tipoEvento;
        historico.origemDesativacaoAnterior = origemDesativacaoAnterior;
        historico.dataDesativacaoAnterior = dataDesativacaoAnterior;
        historico.desativadorAnterior = desativadorAnterior;
        historico.desativadorAnteriorIdSnapshot = desativadorAnteriorIdSnapshot;
        historico.motivoDesativacaoAnterior = motivoDesativacaoAnterior;
        historico.dataArquivamentoAnterior = dataArquivamentoAnterior;
        historico.arquivadorAnterior = arquivadorAnterior;
        historico.arquivadorAnteriorIdSnapshot = arquivadorAnteriorIdSnapshot;
        historico.motivoArquivamentoAnterior = motivoArquivamentoAnterior;
        historico.usuario = usuario;
        historico.usuarioIdSnapshot = usuario == null ? null : usuario.getId();
        historico.autoridadeAtor = autoridadeAtor;
        historico.motivo = motivo;
        return historico;
    }
}
