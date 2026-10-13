package com.amoudi.financial.service;

import com.amoudi.financial.dto.ParsedReport;
import com.amoudi.financial.dto.ParsedSalesRow;
import com.amoudi.financial.dto.RowParsingFailure;
import com.amoudi.financial.enums.ImportErrorType;
import com.amoudi.financial.exception.FileValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;

/**
 * Production Apache POI implementation of ExcelParserService.
 * Reads ONLY authoritative Sheet2 and extracts financial transactions with G.TOT amounts.
 */
@Slf4j
@Service
public class ApachePoiExcelParserService implements ExcelParserService {

    private static final String TARGET_SHEET_NAME = "Sheet2";

    // Required column headers in Sheet2
    private static final String COL_NUMBER = "number";
    private static final String COL_ISSUED = "issued";
    private static final String COL_PASSENGER = "passenger";
    private static final String COL_SECTOR = "sector";
    private static final String COL_BASE_FARE = "base fare";
    private static final String COL_TAX = "tax";
    private static final String COL_TOTAL = "total";
    private static final String COL_S_CHR = "s.chr";
    private static final String COL_G_TOT = "g.tot";
    private static final String COL_PAYMENT_TEXT = "form of payment free text";
    private static final String COL_PNR = "pnr";

    // Optional column headers
    private static final String COL_PCC = "pcc";
    private static final String COL_SON = "son";

    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    public ParsedReport parse(InputStream inputStream, String originalFilename) {
        if (inputStream == null) {
            throw new FileValidationException("File input stream cannot be null");
        }

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = findSheet2(workbook);
            if (sheet == null) {
                throw new FileValidationException(
                        String.format("Workbook '%s' does not contain authoritative sheet '%s'", originalFilename, TARGET_SHEET_NAME));
            }

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, Integer> columnIndices = detectHeaders(sheet);

            ParsedReport report = ParsedReport.builder()
                    .sheetName(sheet.getSheetName())
                    .validRows(new ArrayList<>())
                    .parsingErrors(new ArrayList<>())
                    .build();

            int headerRowIndex = columnIndices.get("__header_row_index__");
            int lastRowNum = sheet.getLastRowNum();
            int totalRowsDetected = 0;
            LocalDate detectedReportDate = null;

            for (int r = headerRowIndex + 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                // Skip summary/totals row at bottom
                if (isSummaryRow(row, columnIndices)) {
                    log.debug("Skipping summary row at line {}", r + 1);
                    continue;
                }

                totalRowsDetected++;
                int sourceRowNumber = r + 1; // 1-indexed

                try {
                    ParsedSalesRow parsedRow = parseRow(row, sourceRowNumber, columnIndices, evaluator);
                    if (detectedReportDate == null && parsedRow.getSaleDate() != null) {
                        detectedReportDate = parsedRow.getSaleDate();
                    }
                    report.getValidRows().add(parsedRow);
                } catch (RowValidationException e) {
                    report.getParsingErrors().add(RowParsingFailure.builder()
                            .rowNumber(sourceRowNumber)
                            .ticketNumber(e.getTicketNumber())
                            .sourceAgencyName(e.getSourceAgencyName())
                            .errorType(e.getErrorType())
                            .message(e.getMessage())
                            .build());
                }
            }

            report.setTotalRowsDetected(totalRowsDetected);
            report.setReportDate(detectedReportDate != null ? detectedReportDate : LocalDate.now());

            log.info("Finished parsing '{}': detected {} rows, {} valid, {} errors",
                    originalFilename, totalRowsDetected, report.getValidRows().size(), report.getParsingErrors().size());
            return report;

        } catch (FileValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Excel workbook '{}': {}", originalFilename, e.getMessage(), e);
            throw new FileValidationException("Invalid or corrupted Excel file: " + e.getMessage());
        }
    }

    private Sheet findSheet2(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet s = workbook.getSheetAt(i);
            if (TARGET_SHEET_NAME.equalsIgnoreCase(s.getSheetName().trim())) {
                return s;
            }
        }
        return null;
    }

    private Map<String, Integer> detectHeaders(Sheet sheet) {
        Map<String, Integer> columnMap = new HashMap<>();

        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 10); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            Map<String, Integer> candidateMap = new HashMap<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell == null) continue;
                String header = dataFormatter.formatCellValue(cell).trim().toLowerCase(Locale.ROOT);
                if (StringUtils.hasText(header)) {
                    candidateMap.put(header, c);
                }
            }

            // Check if this row contains required headers
            if (candidateMap.containsKey(COL_NUMBER) &&
                    candidateMap.containsKey(COL_ISSUED) &&
                    candidateMap.containsKey(COL_G_TOT) &&
                    candidateMap.containsKey(COL_PAYMENT_TEXT)) {

                candidateMap.put("__header_row_index__", r);
                columnMap = candidateMap;
                break;
            }
        }

        if (!columnMap.containsKey("__header_row_index__")) {
            throw new FileValidationException(
                    "Sheet2 is missing required header columns (Number, Issued, G.TOT, Form Of Payment Free Text, etc.)");
        }

        // Validate mandatory columns
        List<String> required = List.of(COL_NUMBER, COL_ISSUED, COL_PASSENGER, COL_SECTOR, COL_G_TOT, COL_PAYMENT_TEXT, COL_PNR);
        for (String req : required) {
            if (!columnMap.containsKey(req)) {
                throw new FileValidationException("Missing required column in Sheet2: " + req);
            }
        }

        return columnMap;
    }

    private boolean isRowEmpty(Row row) {
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = dataFormatter.formatCellValue(cell).trim();
                if (!val.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isSummaryRow(Row row, Map<String, Integer> columnIndices) {
        Integer numberCol = columnIndices.get(COL_NUMBER);
        String ticket = numberCol != null ? getCellString(row.getCell(numberCol)) : "";
        if (StringUtils.hasText(ticket)) {
            return false;
        }

        // Check if row contains 'TOT' or 'TOTAL' in any cell
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null) {
                String val = dataFormatter.formatCellValue(cell).trim().toUpperCase(Locale.ROOT);
                if (val.contains("TOT") || val.contains("SUM(")) {
                    return true;
                }
            }
        }
        return true;
    }

    private ParsedSalesRow parseRow(Row row, int rowNum, Map<String, Integer> colMap, FormulaEvaluator evaluator) {
        String ticketNumber = getCellString(row.getCell(colMap.get(COL_NUMBER)));
        if (!StringUtils.hasText(ticketNumber)) {
            throw new RowValidationException(rowNum, null, null, ImportErrorType.MISSING_REQUIRED_FIELD,
                    "Row is missing ticket number");
        }

        String agencyName = getCellString(row.getCell(colMap.get(COL_PAYMENT_TEXT)));
        if (!StringUtils.hasText(agencyName)) {
            throw new RowValidationException(rowNum, ticketNumber, null, ImportErrorType.UNMATCHED_AGENCY,
                    "Row is missing source agency name (Form Of Payment Free Text)");
        }

        LocalDate saleDate = parseDate(row.getCell(colMap.get(COL_ISSUED)), rowNum, ticketNumber, agencyName);
        BigDecimal grandTotal = parseAmount(row.getCell(colMap.get(COL_G_TOT)), evaluator, rowNum, ticketNumber, agencyName);

        BigDecimal baseFare = colMap.containsKey(COL_BASE_FARE)
                ? parseOptionalAmount(row.getCell(colMap.get(COL_BASE_FARE)), evaluator, rowNum, ticketNumber, agencyName, "Base Fare")
                : null;
        BigDecimal tax = colMap.containsKey(COL_TAX)
                ? parseOptionalAmount(row.getCell(colMap.get(COL_TAX)), evaluator, rowNum, ticketNumber, agencyName, "Tax")
                : null;
        BigDecimal totalSale = colMap.containsKey(COL_TOTAL)
                ? parseOptionalAmount(row.getCell(colMap.get(COL_TOTAL)), evaluator, rowNum, ticketNumber, agencyName, "Total")
                : null;
        BigDecimal serviceCharge = colMap.containsKey(COL_S_CHR)
                ? parseOptionalAmount(row.getCell(colMap.get(COL_S_CHR)), evaluator, rowNum, ticketNumber, agencyName, "S.CHR")
                : null;

        // Verify source relationship without silently altering authoritative source values
        if (baseFare != null && tax != null && totalSale != null) {
            if (baseFare.add(tax).compareTo(totalSale) != 0) {
                log.debug("Row {}: Source relationship mismatch: Base Fare ({}) + Tax ({}) != Total ({})",
                        rowNum, baseFare, tax, totalSale);
            }
        }
        if (totalSale != null && serviceCharge != null && grandTotal != null) {
            if (totalSale.add(serviceCharge).compareTo(grandTotal) != 0) {
                log.debug("Row {}: Source relationship mismatch: Total ({}) + S.CHR ({}) != G.TOT ({})",
                        rowNum, totalSale, serviceCharge, grandTotal);
            }
        }

        String passengerName = getCellString(row.getCell(colMap.get(COL_PASSENGER)));
        String sector = getCellString(row.getCell(colMap.get(COL_SECTOR)));
        String pnr = getCellString(row.getCell(colMap.get(COL_PNR)));

        String pcc = colMap.containsKey(COL_PCC) ? getCellString(row.getCell(colMap.get(COL_PCC))) : null;
        String son = colMap.containsKey(COL_SON) ? getCellString(row.getCell(colMap.get(COL_SON))) : null;

        // Parse origin and destination from sector (e.g., ADDMCT or ADDJEDDXBJEDADD)
        String origin = null;
        String destination = null;
        if (StringUtils.hasText(sector)) {
            String cleanSector = sector.replaceAll("[^a-zA-Z]", "").toUpperCase(Locale.ROOT);
            if (cleanSector.length() >= 6) {
                origin = cleanSector.substring(0, 3);
                destination = cleanSector.substring(cleanSector.length() - 3);
            } else if (cleanSector.length() >= 3) {
                origin = cleanSector.substring(0, 3);
                destination = cleanSector;
            }
        }

        // Build deterministic source transaction key using authoritative grandTotal
        String cleanTicket = ticketNumber.replaceAll("\\s+", "");
        String cleanPnr = pnr != null ? pnr.trim() : "";
        String sourceTransactionKey = String.format("TICKET:%s|DATE:%s|AMT:%.2f|PNR:%s",
                cleanTicket, saleDate, grandTotal, cleanPnr);

        return ParsedSalesRow.builder()
                .sourceRowNumber(rowNum)
                .ticketNumber(ticketNumber.trim())
                .saleDate(saleDate)
                .passengerName(StringUtils.hasText(passengerName) ? passengerName.trim() : null)
                .route(StringUtils.hasText(sector) ? sector.trim() : null)
                .origin(origin)
                .destination(destination)
                .baseFare(baseFare)
                .tax(tax)
                .totalSale(totalSale)
                .serviceCharge(serviceCharge)
                .grandTotal(grandTotal)
                .amount(grandTotal) // Maintain compatibility alias
                .currency("ETB")
                .sourceAgencyName(agencyName.trim())
                .pnr(cleanPnr)
                .pcc(pcc)
                .son(son)
                .sourceTransactionKey(sourceTransactionKey)
                .build();
    }

    private BigDecimal parseOptionalAmount(Cell cell, FormulaEvaluator evaluator, int rowNum, String ticket, String agency, String fieldName) {
        if (cell == null) {
            return null;
        }

        try {
            double numericValue;
            if (cell.getCellType() == CellType.BLANK) {
                return null;
            } else if (cell.getCellType() == CellType.FORMULA) {
                CellValue cellValue = evaluator.evaluate(cell);
                if (cellValue == null || cellValue.getCellType() != CellType.NUMERIC) {
                    return null;
                }
                numericValue = cellValue.getNumberValue();
            } else if (cell.getCellType() == CellType.NUMERIC) {
                numericValue = cell.getNumericCellValue();
            } else {
                String strVal = dataFormatter.formatCellValue(cell).trim().replaceAll(",", "");
                if (strVal.isEmpty()) {
                    return null;
                }
                numericValue = Double.parseDouble(strVal);
            }
            return BigDecimal.valueOf(numericValue).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.debug("Row {}: Could not parse optional {} as amount: {}", rowNum, fieldName, e.getMessage());
            return null;
        }
    }

    private LocalDate parseDate(Cell cell, int rowNum, String ticket, String agency) {
        if (cell == null) {
            throw new RowValidationException(rowNum, ticket, agency, ImportErrorType.INVALID_DATE, "Missing sale date");
        }

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                Date date = cell.getDateCellValue();
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }

            String dateStr = dataFormatter.formatCellValue(cell).trim();
            if (!StringUtils.hasText(dateStr)) {
                throw new RowValidationException(rowNum, ticket, agency, ImportErrorType.INVALID_DATE, "Empty sale date");
            }

            // Support formats like "17-Sep", "17-Sep-2026", "2026-09-17", "17/09/2026"
            DateTimeFormatter formatterWithYear = new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("[dd-MMM-yyyy][dd-MMM-yy][yyyy-MM-dd][dd/MM/yyyy][MM/dd/yyyy]")
                    .toFormatter(Locale.ENGLISH);

            try {
                return LocalDate.parse(dateStr, formatterWithYear);
            } catch (Exception ignored) {
                // If year is missing (e.g. "17-Sep"), parse with default current year
                DateTimeFormatter formatterNoYear = new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("[d-MMM][dd-MMM]")
                        .parseDefaulting(ChronoField.YEAR, LocalDate.now().getYear())
                        .toFormatter(Locale.ENGLISH);

                return LocalDate.parse(dateStr, formatterNoYear);
            }
        } catch (RowValidationException rve) {
            throw rve;
        } catch (Exception e) {
            throw new RowValidationException(rowNum, ticket, agency, ImportErrorType.INVALID_DATE,
                    "Invalid date format: " + dataFormatter.formatCellValue(cell));
        }
    }

    private BigDecimal parseAmount(Cell cell, FormulaEvaluator evaluator, int rowNum, String ticket, String agency) {
        if (cell == null) {
            throw new RowValidationException(rowNum, ticket, agency, ImportErrorType.INVALID_AMOUNT, "Missing G.TOT amount");
        }

        try {
            double numericValue;

            if (cell.getCellType() == CellType.FORMULA) {
                CellValue cellValue = evaluator.evaluate(cell);
                if (cellValue == null || cellValue.getCellType() != CellType.NUMERIC) {
                    throw new RowValidationException(rowNum, ticket, agency, ImportErrorType.INVALID_AMOUNT,
                            "G.TOT formula did not evaluate to a numeric value");
                }
                numericValue = cellValue.getNumberValue();
            } else if (cell.getCellType() == CellType.NUMERIC) {
                numericValue = cell.getNumericCellValue();
            } else {
                String strVal = dataFormatter.formatCellValue(cell).trim().replaceAll(",", "");
                numericValue = Double.parseDouble(strVal);
            }

            BigDecimal amount = BigDecimal.valueOf(numericValue).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RowValidationException(rowNum, ticket, agency, ImportErrorType.INVALID_AMOUNT,
                        "Authoritative amount (G.TOT) must be strictly positive (> 0), got: " + amount);
            }
            return amount;
        } catch (RowValidationException rve) {
            throw rve;
        } catch (Exception e) {
            throw new RowValidationException(rowNum, ticket, agency, ImportErrorType.INVALID_AMOUNT,
                    "Failed to parse G.TOT as monetary amount: " + e.getMessage());
        }
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return dataFormatter.formatCellValue(cell).trim();
    }

    private static class RowValidationException extends RuntimeException {
        private final int rowNumber;
        private final String ticketNumber;
        private final String sourceAgencyName;
        private final ImportErrorType errorType;

        public RowValidationException(int rowNumber, String ticketNumber, String sourceAgencyName,
                                      ImportErrorType errorType, String message) {
            super(message);
            this.rowNumber = rowNumber;
            this.ticketNumber = ticketNumber;
            this.sourceAgencyName = sourceAgencyName;
            this.errorType = errorType;
        }

        public int getRowNumber() { return rowNumber; }
        public String getTicketNumber() { return ticketNumber; }
        public String getSourceAgencyName() { return sourceAgencyName; }
        public ImportErrorType getErrorType() { return errorType; }
    }
}
