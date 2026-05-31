package com.exemplo.catalogo;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CatalogoIntegrationTest {

    @Autowired
    private WebApplicationContext contexto;

    @Test
    void deveImportarValidarBaixarErrosEExportar() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(contexto).build();

        byte[] xlsx = gerarPlanilha();

        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "catalogo.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsx);

        MvcResult result = mvc.perform(multipart("/catalogos/importacoes").file(arquivo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLinhas").value(4))
                .andExpect(jsonPath("$.totalErros").value(2))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        long id = Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));

        MvcResult errosAsync = mvc.perform(get("/catalogos/importacoes/{id}/erros", id))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult erros = mvc.perform(asyncDispatch(errosAsync))
                .andExpect(status().isOk())
                .andReturn();

        byte[] csv = erros.getResponse().getContentAsByteArray();
        assertEquals((byte) 0xEF, csv[0], "byte 0 deve ser BOM");
        assertEquals((byte) 0xBB, csv[1], "byte 1 deve ser BOM");
        assertEquals((byte) 0xBF, csv[2], "byte 2 deve ser BOM");
        String texto = new String(csv);
        assertTrue(texto.contains("Preco negativo"), "CSV deve listar erro de preco negativo");
        assertTrue(texto.contains("SKU obrigatorio"), "CSV deve listar erro de SKU faltando");

        MvcResult exportAsync = mvc.perform(get("/catalogos/exportacoes/produtos.xlsx"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult export = mvc.perform(asyncDispatch(exportAsync))
                .andExpect(status().isOk())
                .andReturn();

        byte[] xlsxExportado = export.getResponse().getContentAsByteArray();
        assertTrue(xlsxExportado.length > 0, "Deve exportar xlsx com produtos validos");
        assertEquals((byte) 'P', xlsxExportado[0], "XLSX e um zip, comeca com PK");
        assertEquals((byte) 'K', xlsxExportado[1]);
    }

    private byte[] gerarPlanilha() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("produtos");

            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("SKU");
            h.createCell(1).setCellValue("Nome");
            h.createCell(2).setCellValue("Preco");
            h.createCell(3).setCellValue("Estoque");
            h.createCell(4).setCellValue("Categoria");

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("VIN-001");
            r1.createCell(1).setCellValue("Malbec Reserva");
            r1.createCell(2).setCellValue(89.90);
            r1.createCell(3).setCellValue(120);
            r1.createCell(4).setCellValue("Vinho Tinto");

            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("VIN-002");
            r2.createCell(1).setCellValue("Chardonnay");
            r2.createCell(2).setCellValue(65.00);
            r2.createCell(3).setCellValue(85);
            r2.createCell(4).setCellValue("Vinho Branco");

            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("VIN-003");
            r3.createCell(1).setCellValue("Espumante");
            r3.createCell(2).setCellValue(-10.0);
            r3.createCell(3).setCellValue(40);
            r3.createCell(4).setCellValue("Espumante");

            Row r4 = sheet.createRow(4);
            r4.createCell(0).setCellValue("");
            r4.createCell(1).setCellValue("Sem SKU");
            r4.createCell(2).setCellValue(50.0);
            r4.createCell(3).setCellValue(10);
            r4.createCell(4).setCellValue("Vinho Tinto");

            wb.write(out);
            return out.toByteArray();
        }
    }
}
