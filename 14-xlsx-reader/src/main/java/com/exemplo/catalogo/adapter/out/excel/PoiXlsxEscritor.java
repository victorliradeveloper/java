package com.exemplo.catalogo.adapter.out.excel;

import com.exemplo.catalogo.domain.model.Produto;
import com.exemplo.catalogo.domain.port.out.PlanilhaEscritor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

@Component
public class PoiXlsxEscritor implements PlanilhaEscritor {

    private static final int LINHAS_EM_MEMORIA = 100;

    @Override
    public void escrever(List<Produto> produtos, OutputStream destino) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(LINHAS_EM_MEMORIA)) {
            Sheet sheet = workbook.createSheet("produtos");

            Row cabecalho = sheet.createRow(0);
            cabecalho.createCell(0).setCellValue("SKU");
            cabecalho.createCell(1).setCellValue("Nome");
            cabecalho.createCell(2).setCellValue("Preco");
            cabecalho.createCell(3).setCellValue("Estoque");
            cabecalho.createCell(4).setCellValue("Categoria");

            int numeroLinha = 1;
            for (Produto produto : produtos) {
                Row row = sheet.createRow(numeroLinha++);
                row.createCell(0).setCellValue(produto.getSku());
                row.createCell(1).setCellValue(produto.getNome());
                row.createCell(2).setCellValue(produto.getPreco().doubleValue());
                row.createCell(3).setCellValue(produto.getEstoque());
                row.createCell(4).setCellValue(produto.getCategoria());
            }

            workbook.write(destino);
            workbook.dispose();
        }
    }
}
