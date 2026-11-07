package ar.edu.unlp.sistemanacional.sistema_nacional;

import io.swagger.v3.oas.annotations.media.Schema;

public class Oferta {

    private String codigo_ong;
    private String organizacion_nombre;
    private String recurso_tipo;
    private Integer recurso_cantidad;
    private Boolean habilitado_flag;

    public Oferta() {
    }

    public Oferta(String organizacion_nombre, String recurso_tipo, Integer recurso_cantidad,
                  Boolean habilitado_flag) {
        this.organizacion_nombre = organizacion_nombre;
        this.recurso_tipo = recurso_tipo;
        this.recurso_cantidad = recurso_cantidad;
        this.habilitado_flag = habilitado_flag;
    }

    @Schema(description = "Código estable de la ONG compartido entre sistemas. Se prioriza sobre organizacion_nombre.")
    public String getCodigo_ong() {
        return codigo_ong;
    }

    public void setCodigo_ong(String codigo_ong) {
        this.codigo_ong = codigo_ong;
    }

    @Schema(description = "Campo legado; usar codigo_ong para identificar la ONG entre sistemas.", deprecated = true)
    public String getOrganizacion_nombre() {
        return organizacion_nombre;
    }

    public void setOrganizacion_nombre(String organizacion_nombre) {
        this.organizacion_nombre = organizacion_nombre;
    }

    public String getRecurso_tipo() {
        return recurso_tipo;
    }

    public void setRecurso_tipo(String recurso_tipo) {
        this.recurso_tipo = recurso_tipo;
    }

    public Integer getRecurso_cantidad() {
        return recurso_cantidad;
    }

    public void setRecurso_cantidad(Integer recurso_cantidad) {
        this.recurso_cantidad = recurso_cantidad;
    }

    public Boolean getHabilitado_flag() {
        return habilitado_flag;
    }

    public void setHabilitado_flag(Boolean habilitado_flag) {
        this.habilitado_flag = habilitado_flag;
    }
}
