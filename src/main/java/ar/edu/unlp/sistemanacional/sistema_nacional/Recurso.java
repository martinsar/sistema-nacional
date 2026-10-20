package ar.edu.unlp.sistemanacional.sistema_nacional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "recursos")
public class Recurso {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String tipo;

	@Column(name = "cantidad_total", nullable = false)
	private Integer cantidadTotal;

	@Column(name = "cantidad_bloqueada", nullable = false)
	private Integer cantidadBloqueada;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ong_id", nullable = false)
	private Ong ong;

	protected Recurso() {
	}

	public Recurso(String tipo, Integer cantidadTotal, Integer cantidadBloqueada, Ong ong) {
		this.tipo = tipo;
		this.cantidadTotal = cantidadTotal;
		this.cantidadBloqueada = cantidadBloqueada;
		this.ong = ong;
	}

	public Long getId() {
		return id;
	}

	public String getTipo() {
		return tipo;
	}

	public void setTipo(String tipo) {
		this.tipo = tipo;
	}

	public Integer getCantidadTotal() {
		return cantidadTotal;
	}

	public void setCantidadTotal(Integer cantidadTotal) {
		this.cantidadTotal = cantidadTotal;
	}

	public Integer getCantidadBloqueada() {
		return cantidadBloqueada;
	}

	public void setCantidadBloqueada(Integer cantidadBloqueada) {
		this.cantidadBloqueada = cantidadBloqueada;
	}

	public Ong getOng() {
		return ong;
	}

	public void setOng(Ong ong) {
		this.ong = ong;
	}
}
