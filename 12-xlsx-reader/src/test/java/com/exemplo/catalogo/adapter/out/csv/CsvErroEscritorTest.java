package com.exemplo.catalogo.adapter.out.csv;

import com.exemplo.catalogo.domain.model.LinhaInvalida;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvErroEscritorTest {

    private final CsvErroEscritor escritor = new CsvErroEscritor();

    @Test
    void deveEscreverBomUtf8NoInicioDoOutputStream() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        escritor.escrever(List.of(), out);

        byte[] bytes = out.toByteArray();
        byte[] bomEsperado = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] primeirosBytes = new byte[3];
        System.arraycopy(bytes, 0, primeirosBytes, 0, 3);

        assertArrayEquals(bomEsperado, primeirosBytes,
                "Os 3 primeiros bytes devem ser o BOM UTF-8 (EF BB BF)");
    }

    @Test
    void deveEscreverCabecalhoEPreservarAcentuacao() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        escritor.escrever(List.of(
                new LinhaInvalida(2, "Preço inválido", "VIN-001 | Malbec | -10 | 5 | Vinho Tinto")
        ), out);

        String conteudo = out.toString(StandardCharsets.UTF_8);

        assertTrue(conteudo.contains("linha;motivo;dados"), "Deve conter cabecalho");
        assertTrue(conteudo.contains("Preço inválido"), "Deve preservar acentuacao em UTF-8");
        assertTrue(conteudo.contains("2;Preço inválido;"), "Deve conter dados do erro");
    }
}
