package mx.sivant.catalogo.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "insumo")
@PrimaryKeyJoinColumn(name = "id_insumo")
public class Insumo extends Articulo {

    protected Insumo() {
    }

    public Insumo(String nombre, UnidadMedida unidadMedida) {
        super(nombre, unidadMedida);
    }
}
