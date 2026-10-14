package com.pmplatform.planning;

import com.pmplatform.platform.web.BusinessRuleException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads a Microsoft Project XML file (File > Save As > XML, the MSPDI format).
 *
 * <p>Each task keeps its MS Project start date as "start no earlier than", and its duration in
 * calendar days from start to finish, so the imported dates match MS Project; links are kept
 * so a slip still pushes later work. Finish-to-start and start-to-start links are kept; other
 * link types become finish-to-start (listed in the warnings). Progress is imported when present.
 * DTDs and external entities are refused (XML external entity attacks).
 */
public final class MsProjectImport {

    public static final int MAX_TASKS = 3000;

    public record ImportedTask(int uid, String name, int outlineLevel, boolean summary, LocalDate start,
            LocalDate finish, int durationDays, BigDecimal percent, LocalDate actualStart, LocalDate actualFinish) {
    }

    public record ImportedLink(int predecessorUid, int successorUid, DependencyType type, int lagDays) {
    }

    public record Parsed(LocalDate projectStart, List<ImportedTask> tasks, List<ImportedLink> links,
            List<String> warnings) {
    }

    private MsProjectImport() {
    }

    public static Parsed parse(InputStream xml) {
        Document doc;
        try {
            doc = secureBuilder().parse(xml);
        } catch (SAXException e) {
            throw new BusinessRuleException("This is not a Microsoft Project XML file (" + e.getMessage() + ")");
        } catch (IOException | ParserConfigurationException e) {
            throw new BusinessRuleException("The file could not be read: " + e.getMessage());
        }
        Element root = doc.getDocumentElement();
        if (!"Project".equals(root.getLocalName() == null ? root.getNodeName() : root.getLocalName())) {
            throw new BusinessRuleException("This is not a Microsoft Project XML file: save the plan from MS Project "
                    + "with File > Save As > XML");
        }
        List<String> warnings = new ArrayList<>();
        LocalDate projectStart = date(text(root, "StartDate"));
        List<ImportedTask> tasks = new ArrayList<>();
        List<ImportedLink> links = new ArrayList<>();
        Element tasksEl = child(root, "Tasks");
        if (tasksEl == null) {
            throw new BusinessRuleException("The file has no tasks");
        }
        for (Element t : children(tasksEl, "Task")) {
            int uid = integer(text(t, "UID"), -1);
            if (uid <= 0 || "1".equals(text(t, "IsNull"))) {
                continue; // UID 0 is the project summary row
            }
            String name = text(t, "Name");
            if (name == null || name.isBlank()) {
                continue;
            }
            if (tasks.size() >= MAX_TASKS) {
                throw new BusinessRuleException("The plan has more than " + MAX_TASKS + " tasks");
            }
            boolean summary = "1".equals(text(t, "Summary"));
            boolean milestone = "1".equals(text(t, "Milestone"));
            LocalDate start = date(text(t, "Start"));
            LocalDate finish = date(text(t, "Finish"));
            if (start == null || finish == null) {
                warnings.add("\"" + name + "\" has no dates and was skipped");
                continue;
            }
            int days = summary || milestone ? 0 : (int) ChronoUnit.DAYS.between(start, finish) + 1;
            BigDecimal pct = new BigDecimal(integer(text(t, "PercentComplete"), 0)).min(BigDecimal.valueOf(100));
            LocalDate as = date(text(t, "ActualStart"));
            LocalDate af = date(text(t, "ActualFinish"));
            if (af != null && as == null) {
                as = start;
            }
            tasks.add(new ImportedTask(uid, name.strip().length() > 200 ? name.strip().substring(0, 200) : name.strip(),
                    Math.max(1, integer(text(t, "OutlineLevel"), 1)), summary, start, finish, Math.max(0, Math.min(days, 3650)),
                    pct, as, af));
            for (Element l : children(t, "PredecessorLink")) {
                int pred = integer(text(l, "PredecessorUID"), -1);
                int type = integer(text(l, "Type"), 1);
                // LinkLag is in tenths of a minute; an MS Project day is 8 hours = 4800.
                int lag = (int) Math.round(integer(text(l, "LinkLag"), 0) / 4800.0);
                DependencyType dt = type == 3 ? DependencyType.SS : DependencyType.FS;
                if (type != 1 && type != 3) {
                    warnings.add("\"" + name + "\": a finish-to-finish or start-to-finish link was imported as finish-to-start");
                }
                if (pred > 0) {
                    links.add(new ImportedLink(pred, uid, dt, Math.max(-365, Math.min(365, lag))));
                }
            }
        }
        if (tasks.isEmpty()) {
            throw new BusinessRuleException("The file has no tasks");
        }
        if (projectStart == null) {
            projectStart = tasks.stream().map(ImportedTask::start).min(LocalDate::compareTo).orElseThrow();
        }
        return new Parsed(projectStart, tasks, links, warnings);
    }

    private static DocumentBuilder secureBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.setNamespaceAware(true);
        DocumentBuilder b = f.newDocumentBuilder();
        b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler()); // report through the exception, not stderr
        return b;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n instanceof Element e && name.equals(e.getLocalName() == null ? e.getNodeName() : e.getLocalName())) {
                out.add(e);
            }
        }
        return out;
    }

    private static Element child(Element parent, String name) {
        List<Element> l = children(parent, name);
        return l.isEmpty() ? null : l.getFirst();
    }

    private static String text(Element parent, String name) {
        Element e = child(parent, name);
        return e == null ? null : e.getTextContent().trim();
    }

    private static int integer(String s, int fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static LocalDate date(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(s).toLocalDate();
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }
}
