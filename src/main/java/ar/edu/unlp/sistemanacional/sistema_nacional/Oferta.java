package ar.edu.unlp.sistemanacional.sistema_nacional;

public class Oferta {

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
