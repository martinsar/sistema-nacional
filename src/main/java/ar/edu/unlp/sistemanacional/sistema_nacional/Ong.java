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
import jakarta.persistence.Table;

@Entity
@Table(name = "ongs")
public class Ong {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
        this.nombre = nombre;
        this.tiposRecursos = tipos != null ? tipos : new ArrayList<>(); 
    }

    public Long getId() {
        return id;
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