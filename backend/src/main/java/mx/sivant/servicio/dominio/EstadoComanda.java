package mx.sivant.servicio.dominio;

public enum EstadoComanda {
    ENVIADA, EN_PREPARACION, LISTA, SERVIDA, CANCELADA;

    public boolean esEditable() {
        return this == ENVIADA || this == EN_PREPARACION;
    }

    public boolean esCancelable() {
        return this == ENVIADA || this == EN_PREPARACION || this == LISTA;
    }

    /** Transiciones de preparación y servicio. La cancelación se valida con `esCancelable`. */
    public boolean puedePasarA(EstadoComanda siguiente) {
        return (this == ENVIADA && siguiente == EN_PREPARACION)
                || (this == EN_PREPARACION && siguiente == LISTA)
                || (this == LISTA && siguiente == SERVIDA);
    }
}
