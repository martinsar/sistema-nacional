package ar.edu.unlp.sistemanacional.sistema_nacional;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "ongs", uniqueConstraints = @UniqueConstraint(name = "uk_ongs_codigo_ong", columnNames = "codigo_ong"))
public class Ong {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_ong", length = 100)
    private String codigoOng;

    @Column(nullable = false, length = 150)
    private String nombre;
	
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ong_tipos_recursos", joinColumns = @JoinColumn(name = "ong_id"))
    @Column(name = "tipo", nullable = false, length = 100)
    private List<String> tiposRecursos = new ArrayList<>(); 

    @OneToMany(mappedBy = "ong")
    private List<Recurso> recursos = new ArrayList<>();

    protected Ong() {
    }

    public Ong(String nombre, List<String> tipos) {
        this(null, nombre, tipos);
    }

    public Ong(String codigoOng, String nombre, List<String> tipos) {
        this.codigoOng = codigoOng;
        this.nombre = nombre;
        this.tiposRecursos = tipos != null ? tipos : new ArrayList<>(); 
    }

    @PrePersist
    void generarCodigoOngSiFalta() {
        if (codigoOng == null || codigoOng.isBlank()) {
            codigoOng = "ONG-" + java.util.UUID.randomUUID();
        }
    }

    public Long getId() {
        return id;
    }

    public String getCodigoOng() {
        return codigoOng;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public List<String> getTipo() {
        return this.tiposRecursos; 
    }

    public void setTipo(List<String> tipo) {
    this.tiposRecursos = tipo != null ? new ArrayList<>(tipo) : new ArrayList<>(); 
}

    public List<Recurso> getRecursos() {
        return recursos;
    }
}