package com.pixevent.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeUtilTest {

    @Test
    void toDataUrl_geraPngBase64NoTamanhoPedido() throws Exception {
        String url = QrCodeUtil.toDataUrl("abc123", 300);

        assertThat(url).startsWith("data:image/png;base64,");
        byte[] png = Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1));
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img).isNotNull();
        assertThat(img.getWidth()).isEqualTo(300);
        assertThat(img.getHeight()).isEqualTo(300);
    }

    @Test
    void toBase64_naoTemPrefixoDataUrl() {
        String b64 = QrCodeUtil.toBase64("abc123", 200);
        assertThat(b64).doesNotContain("data:").doesNotContain(",");
        assertThat(Base64.getDecoder().decode(b64)).isNotEmpty();
    }

    @Test
    void tokensDiferentesGeramImagensDiferentes() {
        assertThat(QrCodeUtil.toDataUrl("aaaa", 200)).isNotEqualTo(QrCodeUtil.toDataUrl("bbbb", 200));
    }
}
