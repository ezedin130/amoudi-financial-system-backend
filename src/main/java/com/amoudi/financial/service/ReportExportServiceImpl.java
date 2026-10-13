package com.amoudi.financial.service;

import com.amoudi.financial.dto.AccountStatementResponse;
import com.amoudi.financial.dto.AgingReportResponse;
import com.amoudi.financial.dto.AgingRowResponse;
import com.amoudi.financial.dto.StatementTransactionResponse;
import com.amoudi.financial.exception.ApiException;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service implementation for exporting statements and aging reports to formatted Excel and PDF documents.
 */
@Service
public class ReportExportServiceImpl implements ReportExportService {

    private static final String COMPANY_NAME = "AMOUDI BUSINESS GROUP";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Colors
    private static final Color PDF_NAVY = new Color(30, 58, 138);
    private static final Color PDF_LIGHT_GRAY = new Color(248, 250, 252);
    private static final Color PDF_BORDER_GRAY = new Color(226, 232, 240);

    // Fonts for PDF
    private static final Font PDF_TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, PDF_NAVY);
    private static final Font PDF_SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
    private static final Font PDF_META_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
    private static final Font PDF_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font PDF_BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
    private static final Font PDF_TOTAL_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);

    // ==========================================
    // EXCEL EXPORTS
    // ==========================================

    @Override
    public byte[] exportStatementToExcel(AccountStatementResponse statement) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Account Statement");
            sheet.setDisplayGridlines(true);

            // Setup CellStyles
            DataFormat dataFormat = workbook.createDataFormat();
            short currencyFormat = dataFormat.getFormat("#,##0.00");

            CellStyle titleStyle = workbook.createCellStyle();
            XSSFFont titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle metaLabelStyle = workbook.createCellStyle();
            XSSFFont boldFont = workbook.createFont();
            boldFont.setBold(true);
            metaLabelStyle.setFont(boldFont);

            CellStyle metaValueCurrencyStyle = workbook.createCellStyle();
            metaValueCurrencyStyle.setFont(boldFont);
            metaValueCurrencyStyle.setDataFormat(currencyFormat);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(headerStyle);

            CellStyle textStyle = workbook.createCellStyle();
            setBorder(textStyle);

            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setAlignment(HorizontalAlignment.CENTER);
            setBorder(centerStyle);

            CellStyle amountStyle = workbook.createCellStyle();
            amountStyle.setDataFormat(currencyFormat);
            amountStyle.setAlignment(HorizontalAlignment.RIGHT);
            setBorder(amountStyle);

            CellStyle totalAmountStyle = workbook.createCellStyle();
            totalAmountStyle.setFont(boldFont);
            totalAmountStyle.setDataFormat(currencyFormat);
            totalAmountStyle.setAlignment(HorizontalAlignment.RIGHT);
            setBorder(totalAmountStyle);

            int rowIdx = 0;

            // Company Title
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(COMPANY_NAME);
            titleCell.setCellStyle(titleStyle);

            // Report Title
            Row subTitleRow = sheet.createRow(rowIdx++);
            Cell subTitleCell = subTitleRow.createCell(0);
            subTitleCell.setCellValue("NON-IATA ACCOUNT STATEMENT");
            subTitleCell.setCellStyle(metaLabelStyle);

            rowIdx++; // Blank row

            // Metadata section
            createMetaRow(sheet, rowIdx++, metaLabelStyle, null, "Non-IATA Code:", statement.getNonIataCode());
            createMetaRow(sheet, rowIdx++, metaLabelStyle, null, "Non-IATA Name:", statement.getNonIataName());
            String period = (statement.getDateFrom() != null ? statement.getDateFrom().toString() : "Earliest") +
                    " to " + (statement.getDateTo() != null ? statement.getDateTo().toString() : "Latest");
            createMetaRow(sheet, rowIdx++, metaLabelStyle, null, "Statement Period:", period);
            createMetaRow(sheet, rowIdx++, metaLabelStyle, null, "Currency:", statement.getCurrency());

            rowIdx++; // Blank row

            // Financial Summary Block
            createFinancialMetaRow(sheet, rowIdx++, metaLabelStyle, metaValueCurrencyStyle, "Opening Balance:", statement.getOpeningBalance());
            createFinancialMetaRow(sheet, rowIdx++, metaLabelStyle, metaValueCurrencyStyle, "Total Period Sales (Debit):", statement.getTotalSales());
            createFinancialMetaRow(sheet, rowIdx++, metaLabelStyle, metaValueCurrencyStyle, "Total Period Payments (Credit):", statement.getTotalPayments());
            createFinancialMetaRow(sheet, rowIdx++, metaLabelStyle, metaValueCurrencyStyle, "Closing Balance:", statement.getClosingBalance());

            rowIdx++; // Blank row

            // Table Headers
            String[] headers = {
                    "Date", "Type", "Reference", "Description",
                    "Base Fare (" + statement.getCurrency() + ")",
                    "Tax (" + statement.getCurrency() + ")",
                    "Total (" + statement.getCurrency() + ")",
                    "S.CHR (" + statement.getCurrency() + ")",
                    "Debit / G.TOT (" + statement.getCurrency() + ")",
                    "Credit (" + statement.getCurrency() + ")",
                    "Running Balance (" + statement.getCurrency() + ")"
            };
            Row headerRow = sheet.createRow(rowIdx++);
            headerRow.setHeightInPoints(24);
            for (int col = 0; col < headers.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers[col]);
                cell.setCellStyle(headerStyle);
            }

            // Table Data
            for (StatementTransactionResponse tx : statement.getTransactions()) {
                Row row = sheet.createRow(rowIdx++);
                Cell c0 = row.createCell(0);
                c0.setCellValue(tx.getTransactionDate().toString());
                c0.setCellStyle(centerStyle);

                Cell c1 = row.createCell(1);
                c1.setCellValue(tx.getType().name());
                c1.setCellStyle(centerStyle);

                Cell c2 = row.createCell(2);
                c2.setCellValue(tx.getReference() != null ? tx.getReference() : "");
                c2.setCellStyle(textStyle);

                Cell c3 = row.createCell(3);
                c3.setCellValue(tx.getDescription() != null ? tx.getDescription() : "");
                c3.setCellStyle(textStyle);

                Cell c4 = row.createCell(4);
                if (tx.getBaseFare() != null) {
                    c4.setCellValue(tx.getBaseFare().doubleValue());
                    c4.setCellStyle(amountStyle);
                } else {
                    c4.setCellValue("");
                    c4.setCellStyle(centerStyle);
                }

                Cell c5 = row.createCell(5);
                if (tx.getTax() != null) {
                    c5.setCellValue(tx.getTax().doubleValue());
                    c5.setCellStyle(amountStyle);
                } else {
                    c5.setCellValue("");
                    c5.setCellStyle(centerStyle);
                }

                Cell c6 = row.createCell(6);
                if (tx.getTotalSale() != null) {
                    c6.setCellValue(tx.getTotalSale().doubleValue());
                    c6.setCellStyle(amountStyle);
                } else {
                    c6.setCellValue("");
                    c6.setCellStyle(centerStyle);
                }

                Cell c7 = row.createCell(7);
                if (tx.getServiceCharge() != null) {
                    c7.setCellValue(tx.getServiceCharge().doubleValue());
                    c7.setCellStyle(amountStyle);
                } else {
                    c7.setCellValue("");
                    c7.setCellStyle(centerStyle);
                }

                Cell c8 = row.createCell(8);
                c8.setCellValue(tx.getDebit().doubleValue());
                c8.setCellStyle(amountStyle);

                Cell c9 = row.createCell(9);
                c9.setCellValue(tx.getCredit().doubleValue());
                c9.setCellStyle(amountStyle);

                Cell c10 = row.createCell(10);
                c10.setCellValue(tx.getRunningBalance().doubleValue());
                c10.setCellStyle(amountStyle);
            }

            // Closing Balance Summary Row
            Row closingRow = sheet.createRow(rowIdx++);
            Cell clLabel = closingRow.createCell(3);
            clLabel.setCellValue("CLOSING BALANCE:");
            clLabel.setCellStyle(metaLabelStyle);

            Cell clDebit = closingRow.createCell(8);
            clDebit.setCellValue(statement.getTotalSales().doubleValue());
            clDebit.setCellStyle(totalAmountStyle);

            Cell clCredit = closingRow.createCell(9);
            clCredit.setCellValue(statement.getTotalPayments().doubleValue());
            clCredit.setCellStyle(totalAmountStyle);

            Cell clBalance = closingRow.createCell(10);
            clBalance.setCellValue(statement.getClosingBalance().doubleValue());
            clBalance.setCellStyle(totalAmountStyle);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException("Failed to generate Excel statement: " + e.getMessage());
        }
    }

    @Override
    public byte[] exportAgingToExcel(AgingReportResponse agingReport) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Aging Report");
            sheet.setDisplayGridlines(true);

            DataFormat dataFormat = workbook.createDataFormat();
            short currencyFormat = dataFormat.getFormat("#,##0.00");

            CellStyle titleStyle = workbook.createCellStyle();
            XSSFFont titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle metaLabelStyle = workbook.createCellStyle();
            XSSFFont boldFont = workbook.createFont();
            boldFont.setBold(true);
            metaLabelStyle.setFont(boldFont);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(headerStyle);

            CellStyle textStyle = workbook.createCellStyle();
            setBorder(textStyle);

            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setAlignment(HorizontalAlignment.CENTER);
            setBorder(centerStyle);

            CellStyle amountStyle = workbook.createCellStyle();
            amountStyle.setDataFormat(currencyFormat);
            amountStyle.setAlignment(HorizontalAlignment.RIGHT);
            setBorder(amountStyle);

            CellStyle totalLabelStyle = workbook.createCellStyle();
            totalLabelStyle.setFont(boldFont);
            totalLabelStyle.setAlignment(HorizontalAlignment.RIGHT);
            setBorder(totalLabelStyle);

            CellStyle totalAmountStyle = workbook.createCellStyle();
            totalAmountStyle.setFont(boldFont);
            totalAmountStyle.setDataFormat(currencyFormat);
            totalAmountStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalAmountStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalAmountStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(totalAmountStyle);

            int rowIdx = 0;

            // Company Title
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(COMPANY_NAME);
            titleCell.setCellStyle(titleStyle);

            // Report Title
            Row subTitleRow = sheet.createRow(rowIdx++);
            Cell subTitleCell = subTitleRow.createCell(0);
            subTitleCell.setCellValue("ACCOUNTS RECEIVABLE AGING REPORT");
            subTitleCell.setCellStyle(metaLabelStyle);

            rowIdx++; // Blank row

            createMetaRow(sheet, rowIdx++, metaLabelStyle, null, "As of Date:", agingReport.getAsOfDate().toString());
            createMetaRow(sheet, rowIdx++, metaLabelStyle, null, "Currency:", agingReport.getCurrency());
            createMetaRow(sheet, rowIdx++, metaLabelStyle, null, "Total Accounts:", String.valueOf(agingReport.getTotalAccounts()));

            rowIdx++; // Blank row

            // Table Headers
            String[] headers = {
                    "Code",
                    "Non-IATA Name",
                    "Status",
                    "Current (0-30d)",
                    "31-60 Days",
                    "61-90 Days",
                    "91-120 Days",
                    "120+ Days",
                    "Total Outstanding"
            };

            Row headerRow = sheet.createRow(rowIdx++);
            headerRow.setHeightInPoints(24);
            for (int col = 0; col < headers.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers[col]);
                cell.setCellStyle(headerStyle);
            }

            // Data Rows
            for (AgingRowResponse rowData : agingReport.getRows()) {
                Row row = sheet.createRow(rowIdx++);

                Cell c0 = row.createCell(0);
                c0.setCellValue(rowData.getCode());
                c0.setCellStyle(centerStyle);

                Cell c1 = row.createCell(1);
                c1.setCellValue(rowData.getName());
                c1.setCellStyle(textStyle);

                Cell c2 = row.createCell(2);
                c2.setCellValue(rowData.getStatus().name());
                c2.setCellStyle(centerStyle);

                Cell c3 = row.createCell(3);
                c3.setCellValue(rowData.getCurrent().doubleValue());
                c3.setCellStyle(amountStyle);

                Cell c4 = row.createCell(4);
                c4.setCellValue(rowData.getDays31To60().doubleValue());
                c4.setCellStyle(amountStyle);

                Cell c5 = row.createCell(5);
                c5.setCellValue(rowData.getDays61To90().doubleValue());
                c5.setCellStyle(amountStyle);

                Cell c6 = row.createCell(6);
                c6.setCellValue(rowData.getDays91To120().doubleValue());
                c6.setCellStyle(amountStyle);

                Cell c7 = row.createCell(7);
                c7.setCellValue(rowData.getDays120Plus().doubleValue());
                c7.setCellStyle(amountStyle);

                Cell c8 = row.createCell(8);
                c8.setCellValue(rowData.getTotalOutstanding().doubleValue());
                c8.setCellStyle(amountStyle);
            }

            // Summary Totals Row
            Row totalRow = sheet.createRow(rowIdx++);
            Cell tLabel = totalRow.createCell(1);
            tLabel.setCellValue("GRAND TOTAL:");
            tLabel.setCellStyle(totalLabelStyle);

            Cell tcCurrent = totalRow.createCell(3);
            tcCurrent.setCellValue(agingReport.getTotalCurrent().doubleValue());
            tcCurrent.setCellStyle(totalAmountStyle);

            Cell tc31 = totalRow.createCell(4);
            tc31.setCellValue(agingReport.getTotalDays31To60().doubleValue());
            tc31.setCellStyle(totalAmountStyle);

            Cell tc61 = totalRow.createCell(5);
            tc61.setCellValue(agingReport.getTotalDays61To90().doubleValue());
            tc61.setCellStyle(totalAmountStyle);

            Cell tc91 = totalRow.createCell(6);
            tc91.setCellValue(agingReport.getTotalDays91To120().doubleValue());
            tc91.setCellStyle(totalAmountStyle);

            Cell tc120 = totalRow.createCell(7);
            tc120.setCellValue(agingReport.getTotalDays120Plus().doubleValue());
            tc120.setCellStyle(totalAmountStyle);

            Cell tcTotal = totalRow.createCell(8);
            tcTotal.setCellValue(agingReport.getTotalOutstanding().doubleValue());
            tcTotal.setCellStyle(totalAmountStyle);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException("Failed to generate Excel aging report: " + e.getMessage());
        }
    }

    private void createMetaRow(Sheet sheet, int rowIdx, CellStyle labelStyle, CellStyle valueStyle, String label, String value) {
        Row row = sheet.createRow(rowIdx);
        Cell lCell = row.createCell(0);
        lCell.setCellValue(label);
        lCell.setCellStyle(labelStyle);

        Cell vCell = row.createCell(1);
        vCell.setCellValue(value != null ? value : "");
        if (valueStyle != null) {
            vCell.setCellStyle(valueStyle);
        }
    }

    private void createFinancialMetaRow(Sheet sheet, int rowIdx, CellStyle labelStyle, CellStyle currencyStyle, String label, BigDecimal amount) {
        Row row = sheet.createRow(rowIdx);
        Cell lCell = row.createCell(0);
        lCell.setCellValue(label);
        lCell.setCellStyle(labelStyle);

        Cell vCell = row.createCell(1);
        vCell.setCellValue(amount != null ? amount.doubleValue() : 0.00);
        vCell.setCellStyle(currencyStyle);
    }

    private void setBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    // ==========================================
    // PDF EXPORTS
    // ==========================================

    @Override
    public byte[] exportStatementToPdf(AccountStatementResponse statement) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 25, 25, 25, 25);
            PdfWriter.getInstance(document, out);
            document.open();

            // Header Section
            Paragraph companyPara = new Paragraph(COMPANY_NAME, PDF_TITLE_FONT);
            companyPara.setAlignment(Element.ALIGN_CENTER);
            document.add(companyPara);

            Paragraph reportTitlePara = new Paragraph("NON-IATA ACCOUNT STATEMENT", PDF_SUBTITLE_FONT);
            reportTitlePara.setAlignment(Element.ALIGN_CENTER);
            reportTitlePara.setSpacingAfter(15);
            document.add(reportTitlePara);

            // Metadata / Account details
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setWidths(new float[]{1, 1});

            String periodStr = (statement.getDateFrom() != null ? statement.getDateFrom().toString() : "Earliest") +
                    " to " + (statement.getDateTo() != null ? statement.getDateTo().toString() : "Latest");

            metaTable.addCell(createMetaPdfCell("Account: " + statement.getNonIataCode() + " - " + statement.getNonIataName()));
            metaTable.addCell(createMetaPdfCell("Period: " + periodStr));
            metaTable.addCell(createMetaPdfCell("Currency: " + statement.getCurrency()));
            metaTable.addCell(createMetaPdfCell("Generated At: " + LocalDateTime.now().format(DATE_TIME_FORMATTER)));
            metaTable.setSpacingAfter(12);
            document.add(metaTable);

            // Summary Card Table
            PdfPTable summaryTable = new PdfPTable(4);
            summaryTable.setWidthPercentage(100);
            summaryTable.setWidths(new float[]{1, 1, 1, 1});

            summaryTable.addCell(createSummaryHeaderCell("Opening Balance"));
            summaryTable.addCell(createSummaryHeaderCell("Period Sales (Debit)"));
            summaryTable.addCell(createSummaryHeaderCell("Period Payments (Credit)"));
            summaryTable.addCell(createSummaryHeaderCell("Closing Balance"));

            summaryTable.addCell(createSummaryValueCell(formatAmount(statement.getOpeningBalance())));
            summaryTable.addCell(createSummaryValueCell(formatAmount(statement.getTotalSales())));
            summaryTable.addCell(createSummaryValueCell(formatAmount(statement.getTotalPayments())));
            summaryTable.addCell(createSummaryValueCell(formatAmount(statement.getClosingBalance())));
            summaryTable.setSpacingAfter(15);
            document.add(summaryTable);

            // Transactions Table
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.5f, 1.2f, 1.8f, 3.5f, 1.8f, 1.8f, 2.0f});

            String[] headers = {"Date", "Type", "Reference", "Description", "Debit", "Credit", "Balance"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, PDF_HEADER_FONT));
                cell.setBackgroundColor(PDF_NAVY);
                cell.setPadding(5);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            boolean alternate = false;
            for (StatementTransactionResponse tx : statement.getTransactions()) {
                Color rowBg = alternate ? PDF_LIGHT_GRAY : Color.WHITE;
                alternate = !alternate;

                table.addCell(createDataPdfCell(tx.getTransactionDate().toString(), Element.ALIGN_CENTER, rowBg));
                table.addCell(createDataPdfCell(tx.getType().name(), Element.ALIGN_CENTER, rowBg));
                table.addCell(createDataPdfCell(tx.getReference() != null ? tx.getReference() : "", Element.ALIGN_LEFT, rowBg));
                table.addCell(createDataPdfCell(tx.getDescription() != null ? tx.getDescription() : "", Element.ALIGN_LEFT, rowBg));
                table.addCell(createDataPdfCell(formatAmount(tx.getDebit()), Element.ALIGN_RIGHT, rowBg));
                table.addCell(createDataPdfCell(formatAmount(tx.getCredit()), Element.ALIGN_RIGHT, rowBg));
                table.addCell(createDataPdfCell(formatAmount(tx.getRunningBalance()), Element.ALIGN_RIGHT, rowBg));
            }

            // Summary Totals Row at the bottom
            PdfPCell lbl = new PdfPCell(new Phrase("CLOSING BALANCE", PDF_TOTAL_FONT));
            lbl.setColspan(4);
            lbl.setHorizontalAlignment(Element.ALIGN_RIGHT);
            lbl.setPadding(5);
            table.addCell(lbl);

            table.addCell(createTotalPdfCell(formatAmount(statement.getTotalSales())));
            table.addCell(createTotalPdfCell(formatAmount(statement.getTotalPayments())));
            table.addCell(createTotalPdfCell(formatAmount(statement.getClosingBalance())));

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException("Failed to generate PDF statement: " + e.getMessage());
        }
    }

    @Override
    public byte[] exportAgingToPdf(AgingReportResponse agingReport) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Landscape A4 for 9-column Aging report
            Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
            PdfWriter.getInstance(document, out);
            document.open();

            // Header Section
            Paragraph companyPara = new Paragraph(COMPANY_NAME, PDF_TITLE_FONT);
            companyPara.setAlignment(Element.ALIGN_CENTER);
            document.add(companyPara);

            Paragraph reportTitlePara = new Paragraph("ACCOUNTS RECEIVABLE AGING REPORT", PDF_SUBTITLE_FONT);
            reportTitlePara.setAlignment(Element.ALIGN_CENTER);
            reportTitlePara.setSpacingAfter(12);
            document.add(reportTitlePara);

            // Metadata
            PdfPTable metaTable = new PdfPTable(3);
            metaTable.setWidthPercentage(100);
            metaTable.setWidths(new float[]{1, 1, 1});

            metaTable.addCell(createMetaPdfCell("As of Date: " + agingReport.getAsOfDate()));
            metaTable.addCell(createMetaPdfCell("Currency: " + agingReport.getCurrency()));
            metaTable.addCell(createMetaPdfCell("Total Accounts: " + agingReport.getTotalAccounts()));
            metaTable.setSpacingAfter(12);
            document.add(metaTable);

            // Aging Data Table
            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.2f, 2.5f, 1.1f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 1.8f});

            String[] headers = {
                    "Code", "Name", "Status", "Current (0-30)", "31-60d", "61-90d", "91-120d", "120+d", "Total"
            };

            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, PDF_HEADER_FONT));
                cell.setBackgroundColor(PDF_NAVY);
                cell.setPadding(5);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            boolean alternate = false;
            for (AgingRowResponse row : agingReport.getRows()) {
                Color rowBg = alternate ? PDF_LIGHT_GRAY : Color.WHITE;
                alternate = !alternate;

                table.addCell(createDataPdfCell(row.getCode(), Element.ALIGN_CENTER, rowBg));
                table.addCell(createDataPdfCell(row.getName(), Element.ALIGN_LEFT, rowBg));
                table.addCell(createDataPdfCell(row.getStatus().name(), Element.ALIGN_CENTER, rowBg));
                table.addCell(createDataPdfCell(formatAmount(row.getCurrent()), Element.ALIGN_RIGHT, rowBg));
                table.addCell(createDataPdfCell(formatAmount(row.getDays31To60()), Element.ALIGN_RIGHT, rowBg));
                table.addCell(createDataPdfCell(formatAmount(row.getDays61To90()), Element.ALIGN_RIGHT, rowBg));
                table.addCell(createDataPdfCell(formatAmount(row.getDays91To120()), Element.ALIGN_RIGHT, rowBg));
                table.addCell(createDataPdfCell(formatAmount(row.getDays120Plus()), Element.ALIGN_RIGHT, rowBg));
                table.addCell(createDataPdfCell(formatAmount(row.getTotalOutstanding()), Element.ALIGN_RIGHT, rowBg));
            }

            // Summary Totals Row
            PdfPCell lbl = new PdfPCell(new Phrase("GRAND TOTAL", PDF_TOTAL_FONT));
            lbl.setColspan(3);
            lbl.setHorizontalAlignment(Element.ALIGN_RIGHT);
            lbl.setPadding(5);
            table.addCell(lbl);

            table.addCell(createTotalPdfCell(formatAmount(agingReport.getTotalCurrent())));
            table.addCell(createTotalPdfCell(formatAmount(agingReport.getTotalDays31To60())));
            table.addCell(createTotalPdfCell(formatAmount(agingReport.getTotalDays61To90())));
            table.addCell(createTotalPdfCell(formatAmount(agingReport.getTotalDays91To120())));
            table.addCell(createTotalPdfCell(formatAmount(agingReport.getTotalDays120Plus())));
            table.addCell(createTotalPdfCell(formatAmount(agingReport.getTotalOutstanding())));

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException("Failed to generate PDF aging report: " + e.getMessage());
        }
    }

    private PdfPCell createMetaPdfCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, PDF_META_FONT));
        cell.setBorder(0);
        cell.setPadding(3);
        return cell;
    }

    private PdfPCell createSummaryHeaderCell(String title) {
        PdfPCell cell = new PdfPCell(new Phrase(title, PDF_SUBTITLE_FONT));
        cell.setBackgroundColor(PDF_LIGHT_GRAY);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(6);
        return cell;
    }

    private PdfPCell createSummaryValueCell(String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value, PDF_TOTAL_FONT));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(6);
        return cell;
    }

    private PdfPCell createDataPdfCell(String text, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, PDF_BODY_FONT));
        cell.setHorizontalAlignment(align);
        cell.setBackgroundColor(bg);
        cell.setPadding(4);
        cell.setBorderColor(PDF_BORDER_GRAY);
        return cell;
    }

    private PdfPCell createTotalPdfCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, PDF_TOTAL_FONT));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setPadding(5);
        cell.setBackgroundColor(PDF_LIGHT_GRAY);
        return cell;
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return String.format("%,.2f", amount);
    }
}
