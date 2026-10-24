package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// Registro de la cantidad de un recurso que se bloqueó para una convocatoria
@Entity
@Table(name = "adjudicaciones")
public class Adjudicacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "convocatoria_id", nullable = false)
	private Long convocatoriaId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ong_id", nullable = false)
	private Ong ong;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recurso_id", nullable = false)
	private Recurso recurso;

	@Column(nullable = false)
	private Integer cantidad;

	@Column(nullable = false)
	private boolean liberada = false;

	@Column(name = "fecha_adjudicacion", nullable = false)
	private LocalDateTime fechaAdjudicacion = LocalDateTime.now();

	@Column(name = "fecha_liberacion")
	private LocalDateTime fechaLiberacion;

	protected Adjudicacion() {
	}

	public Adjudicacion(Long convocatoriaId, Ong ong, Recurso recurso, Integer cantidad) {
		this.convocatoriaId = convocatoriaId;
		this.ong = ong;
		this.recurso = recurso;
		this.cantidad = cantidad;
	}

	public Long getId() {
		return id;
	}

	public Long getConvocatoriaId() {
		return convocatoriaId;
	}

	public Ong getOng() {
		return ong;
	}

	public Recurso getRecurso() {
		return recurso;
	}

	public Integer getCantidad() {
		return cantidad;
	}

	public boolean isLiberada() {
		return liberada;
	}

	public LocalDateTime getFechaAdjudicacion() {
		return fechaAdjudicacion;
	}

	public LocalDateTime getFechaLiberacion() {
		return fechaLiberacion;
	}

	public void marcarLiberada() {
		this.liberada = true;
		this.fechaLiberacion = LocalDateTime.now();
	}
}
