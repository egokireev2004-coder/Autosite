package com.avex.vol1.service;

import com.avex.vol1.model.CrossNumber;
import com.avex.vol1.model.Part;
import com.avex.vol1.repository.PartRepository;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ImportService {

    static {
        IOUtils.setByteArrayMaxOverride(250_000_000);
    }

    // catalog.xlsx — колонки
    private static final int CAT_BRAND  = 1;
    private static final int CAT_TITLE  = 3;
    private static final int CAT_CROSS  = 5;
    private static final int CAT_NUMBER = 6;
    private static final int CAT_STOCK  = 7;
    private static final int CAT_PRICE  = 12;

    // instock.xlsx — колонки
    private static final int STK_TITLE  = 1;
    private static final int STK_BRAND  = 2;
    private static final int STK_CROSS  = 3;
    private static final int STK_NUMBER = 4;
    private static final int STK_STOCK  = 5;
    private static final int STK_PRICE  = 6;

    private final PartRepository partRepository;

    public ImportService(PartRepository partRepository) {
        this.partRepository = partRepository;
    }

    // Вызывается вручную через /admin/import
    @Transactional
    public String importAll() {
        StringBuilder log = new StringBuilder();

        log.append("Удаление старых данных каталога...\n");
        partRepository.deleteBySource("catalog");

        log.append("Импорт catalog.xlsx...\n");
        int catCount = importFile("catalog.xlsx", "catalog",
                CAT_TITLE, CAT_BRAND, CAT_CROSS, CAT_NUMBER, CAT_STOCK, CAT_PRICE);
        log.append("Импортировано из каталога: ").append(catCount).append(" записей\n");

        log.append("Удаление старых данных наличия...\n");
        partRepository.deleteBySource("instock");

        log.append("Импорт instock.xlsx...\n");
        int stkCount = importFile("instock.xlsx", "instock",
                STK_TITLE, STK_BRAND, STK_CROSS, STK_NUMBER, STK_STOCK, STK_PRICE);
        log.append("Импортировано из наличия: ").append(stkCount).append(" записей\n");

        log.append("Готово!");
        return log.toString();
    }

    private int importFile(String path, String source,
                           int titleCol, int brandCol, int crossCol,
                           int numberCol, int stockCol, int priceCol) {
        List<RowData> rows = loadAllRows(path, titleCol, brandCol, crossCol, numberCol, stockCol, priceCol);

        List<Part> batch = new ArrayList<>();
        for (RowData row : rows) {
            if (row.partNumber == null || row.partNumber.isBlank()) continue;

            Part part = new Part();
            part.setPartNumber(row.partNumber);
            part.setPartNumberNorm(normalize(row.partNumber));
            part.setBrand(row.brand);
            part.setTitle(row.title);
            part.setPrice(row.price);
            part.setSource(source);

            // Статус
            if ("instock".equals(source)) {
                part.setStockStatus("instock");
            } else {
                boolean hasStock = "есть".equalsIgnoreCase(row.stockStatus.trim());
                part.setStockStatus(hasStock ? "order" : "none");
            }

            // Кросс-номера
            for (String cross : row.crossNumbers) {
                if (!cross.isBlank()) {
                    part.getCrossNumbers().add(
                            new CrossNumber(part, cross, normalize(cross)));
                }
            }

            batch.add(part);

            // Сохраняем батчами по 500 чтобы не перегружать память
            if (batch.size() >= 500) {
                partRepository.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) partRepository.saveAll(batch);

        return rows.size();
    }

    private List<RowData> loadAllRows(String path,
                                      int titleCol, int brandCol, int crossCol,
                                      int numberCol, int stockCol, int priceCol) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            OPCPackage pkg    = OPCPackage.open(is);
            XSSFReader xr     = new XSSFReader(pkg);
            SharedStrings sst = xr.getSharedStringsTable();

            SAXParserFactory spf = SAXParserFactory.newInstance();
            XMLReader xmlReader  = spf.newSAXParser().getXMLReader();

            RowCollectorHandler handler = new RowCollectorHandler(
                    sst, titleCol, brandCol, crossCol, numberCol, stockCol, priceCol);
            xmlReader.setContentHandler(handler);
            xmlReader.parse(new InputSource(xr.getSheetsData().next()));

            return handler.getResults();
        } catch (Exception e) {
            System.err.println("Ошибка загрузки " + path + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.replace("-", "").replace(".", "")
                .replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------
    // Внутренние классы SAX
    // ---------------------------------------------------------------

    static class RowData {
        String title       = "";
        String brand       = "";
        List<String> crossNumbers = new ArrayList<>();
        String partNumber  = "";
        String stockStatus = "";
        String price       = "";
    }

    class RowCollectorHandler extends DefaultHandler {
        private final SharedStrings sst;
        private final int titleCol, brandCol, crossCol, numberCol, stockCol, priceCol;
        private final Set<Integer> collectCols;
        private final List<RowData> results = new ArrayList<>();

        private RowData currentRow;
        private int currentColIndex = -1;
        private boolean isSharedString = false;
        private boolean isRelevantCell = false;
        private final StringBuilder cellValue = new StringBuilder();

        RowCollectorHandler(SharedStrings sst,
                            int titleCol, int brandCol, int crossCol,
                            int numberCol, int stockCol, int priceCol) {
            this.sst = sst;
            this.titleCol  = titleCol;
            this.brandCol  = brandCol;
            this.crossCol  = crossCol;
            this.numberCol = numberCol;
            this.stockCol  = stockCol;
            this.priceCol  = priceCol;
            this.collectCols = new HashSet<>(Arrays.asList(
                    titleCol, brandCol, crossCol, numberCol, stockCol, priceCol));
        }

        public List<RowData> getResults() { return results; }

        @Override
        public void startElement(String uri, String local, String qName, Attributes attrs) {
            if ("row".equals(qName)) {
                currentRow = new RowData();
            } else if ("c".equals(qName) && currentRow != null) {
                currentColIndex = colIndex(attrs.getValue("r"));
                isSharedString  = "s".equals(attrs.getValue("t"));
                isRelevantCell  = collectCols.contains(currentColIndex);
                cellValue.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (isRelevantCell) cellValue.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String local, String qName) {
            if ("v".equals(qName) && isRelevantCell && currentRow != null) {
                String raw = cellValue.toString().trim();
                String val = isSharedString
                        ? sst.getItemAt(Integer.parseInt(raw)).getString()
                        : raw;

                if (currentColIndex == titleCol)  currentRow.title = val;
                if (currentColIndex == brandCol)   currentRow.brand = val;
                if (currentColIndex == numberCol)  currentRow.partNumber = val;
                if (currentColIndex == stockCol)   currentRow.stockStatus = val;
                if (currentColIndex == priceCol)   currentRow.price = val;
                if (currentColIndex == crossCol) {
                    for (String part : val.split("[,，;]")) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) currentRow.crossNumbers.add(trimmed);
                    }
                }
                isRelevantCell = false;
            }

            if ("row".equals(qName) && currentRow != null) {
                results.add(currentRow);
                currentRow = null;
            }
        }

        private int colIndex(String ref) {
            if (ref == null) return -1;
            int idx = 0;
            for (char c : ref.toCharArray()) {
                if (!Character.isLetter(c)) break;
                idx = idx * 26 + (Character.toUpperCase(c) - 'A' + 1);
            }
            return idx - 1;
        }
    }
}