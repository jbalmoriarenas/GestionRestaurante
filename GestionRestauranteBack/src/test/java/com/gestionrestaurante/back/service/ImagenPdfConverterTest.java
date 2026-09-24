package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

class ImagenPdfConverterTest {

    @Test
    void convertirAPdf_generaUnPdfDeUnaSolaPaginaAPartirDeUnaImagenVertical() throws IOException {
        byte[] imagen = pngDe(600, 800);

        byte[] pdf = ImagenPdfConverter.convertirAPdf(imagen);

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            assertThat(documento.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void convertirAPdf_usaPaginaHorizontalSiLaFotoEsMasAnchaQueAlta() throws IOException {
        byte[] imagen = pngDe(1200, 400);

        byte[] pdf = ImagenPdfConverter.convertirAPdf(imagen);

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            var pagina = documento.getPage(0);
            assertThat(pagina.getMediaBox().getWidth()).isGreaterThan(pagina.getMediaBox().getHeight());
        }
    }

    @Test
    void convertirAPdf_bytesQueNoSonUnaImagenLanzaIOException() {
        byte[] noEsUnaImagen = "esto no es una imagen".getBytes();

        assertThatThrownBy(() -> ImagenPdfConverter.convertirAPdf(noEsUnaImagen))
                .isInstanceOf(IOException.class);
    }

    @Test
    void nombrePdfDesde_sustituyeLaExtensionPorPdf() {
        assertThat(ImagenPdfConverter.nombrePdfDesde("foto.jpg")).isEqualTo("foto.pdf");
        assertThat(ImagenPdfConverter.nombrePdfDesde("factura.makro.png")).isEqualTo("factura.makro.pdf");
    }

    @Test
    void nombrePdfDesde_sinExtensionOriginalAñadePdf() {
        assertThat(ImagenPdfConverter.nombrePdfDesde("foto")).isEqualTo("foto.pdf");
    }

    @Test
    void nombrePdfDesde_sinNombreOriginalDevuelveUnoPorDefecto() {
        assertThat(ImagenPdfConverter.nombrePdfDesde(null)).isEqualTo("factura.pdf");
        assertThat(ImagenPdfConverter.nombrePdfDesde("")).isEqualTo("factura.pdf");
    }

    private static byte[] pngDe(int ancho, int alto) throws IOException {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return salida.toByteArray();
    }
}
