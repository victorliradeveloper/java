package com.exemplo.catalogo.adapter.out.excel;

import com.exemplo.catalogo.domain.model.LinhaPlanilha;
import com.exemplo.catalogo.domain.port.out.PlanilhaLeitor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Component
public class PoiXlsxLeitor implements PlanilhaLeitor {

    @Override
    public Stream<LinhaPlanilha> ler(InputStream entrada) throws IOException {
        Workbook workbook = new XSSFWorkbook(entrada);
        Sheet sheet = workbook.getSheetAt(0);

        Spliterator<Row> spliterator = Spliterators.spliteratorUnknownSize(
                sheet.rowIterator(), Spliterator.ORDERED);

        return StreamSupport.stream(spliterator, false)
                .skip(1)
                .map(this::mapearLinha)
                .onClose(() -> {
                    try {
                        workbook.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private LinhaPlanilha mapearLinha(Row row) {
        return new LinhaPlanilha(
                row.getRowNum() + 1,
                getString(row, 0),
                getString(row, 1),
                getDecimal(row, 2),
                getInt(row, 3),
                getString(row, 4));
    }

    private String getString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf(cell.getNumericCellValue());
        }
        return cell.getStringCellValue() == null ? "" : cell.getStringCellValue().trim();
    }

    private BigDecimal getDecimal(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        try {
            return new BigDecimal(cell.getStringCellValue().trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getInt(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        }
        try {
            return Integer.parseInt(cell.getStringCellValue().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
