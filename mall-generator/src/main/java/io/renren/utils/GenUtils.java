package io.renren.utils;

import io.renren.config.MongoManager;
import io.renren.entity.ColumnEntity;
import io.renren.entity.TableEntity;
import io.renren.entity.mongo.MongoDefinition;
import io.renren.entity.mongo.MongoGeneratorEntity;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ä»£ç ç”Ÿæˆå™¨   å·¥å…·ç±»
 *
 * @author chenshun
 * @email sunlightcs@gmail.com
 * @date 2016å¹´12æœˆ19æ—¥ ä¸‹åˆ11:40:24
 */
public class GenUtils {

    private static String currentTableName;

    public static List<String> getTemplates() {
        List<String> templates = new ArrayList<String>();
        templates.add("template/Entity.java.vm");
        templates.add("template/Dao.xml.vm");

        templates.add("template/menu.sql.vm");

        templates.add("template/Service.java.vm");
        templates.add("template/ServiceImpl.java.vm");
        templates.add("template/Controller.java.vm");
        templates.add("template/Dao.java.vm");

        templates.add("template/index.vue.vm");
        templates.add("template/add-or-update.vue.vm");
        if (MongoManager.isMongo()) {
            // mongoä¸éœ€è¦mapperã€sql   å®žä½“ç±»éœ€è¦æ›¿æ¢
            templates.remove(0);
            templates.remove(1);
            templates.remove(2);
            templates.add("template/MongoEntity.java.vm");
        }
        return templates;
    }

    public static List<String> getMongoChildTemplates() {
        List<String> templates = new ArrayList<String>();
        templates.add("template/MongoChildrenEntity.java.vm");
        return templates;
    }

    /**
     * ç”Ÿæˆä»£ç 
     */
    public static void generatorCode(Map<String, String> table,
                                     List<Map<String, String>> columns, ZipOutputStream zip) {
        //é…ç½®ä¿¡æ¯
        Configuration config = getConfig();
        boolean hasBigDecimal = false;
        boolean hasList = false;
        //è¡¨ä¿¡æ¯
        TableEntity tableEntity = new TableEntity();
        tableEntity.setTableName(table.get("tableName"));
        tableEntity.setComments(table.get("tableComment"));
        //è¡¨åè½¬æ¢æˆJavaç±»å
        String className = tableToJava(tableEntity.getTableName(), config.getStringArray("tablePrefix"));
        tableEntity.setClassName(className);
        tableEntity.setClassname(StringUtils.uncapitalize(className));

        //åˆ—ä¿¡æ¯
        List<ColumnEntity> columsList = new ArrayList<>();
        for (Map<String, String> column : columns) {
            ColumnEntity columnEntity = new ColumnEntity();
            columnEntity.setColumnName(column.get("columnName"));
            columnEntity.setDataType(column.get("dataType"));
            columnEntity.setComments(column.get("columnComment"));
            columnEntity.setExtra(column.get("extra"));

            //åˆ—åè½¬æ¢æˆJavaå±žæ€§å
            String attrName = columnToJava(columnEntity.getColumnName());
            columnEntity.setAttrName(attrName);
            columnEntity.setAttrname(StringUtils.uncapitalize(attrName));

            //åˆ—çš„æ•°æ®ç±»åž‹ï¼Œè½¬æ¢æˆJavaç±»åž‹
            String attrType = config.getString(columnEntity.getDataType(), columnToJava(columnEntity.getDataType()));
            columnEntity.setAttrType(attrType);


            if (!hasBigDecimal && attrType.equals("BigDecimal")) {
                hasBigDecimal = true;
            }
            if (!hasList && "array".equals(columnEntity.getExtra())) {
                hasList = true;
            }
            //æ˜¯å¦ä¸»é”®
            if ("PRI".equalsIgnoreCase(column.get("columnKey")) && tableEntity.getPk() == null) {
                tableEntity.setPk(columnEntity);
            }

            columsList.add(columnEntity);
        }
        tableEntity.setColumns(columsList);

        //æ²¡ä¸»é”®ï¼Œåˆ™ç¬¬ä¸€ä¸ªå­—æ®µä¸ºä¸»é”®
        if (tableEntity.getPk() == null) {
            tableEntity.setPk(tableEntity.getColumns().get(0));
        }

        //è®¾ç½®velocityèµ„æºåŠ è½½å™¨
        Properties prop = new Properties();
        prop.put("file.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        Velocity.init(prop);
        String mainPath = config.getString("mainPath");
        mainPath = StringUtils.isBlank(mainPath) ? "io.renren" : mainPath;
        //å°è£…æ¨¡æ¿æ•°æ®
        Map<String, Object> map = new HashMap<>();
        map.put("tableName", tableEntity.getTableName());
        map.put("comments", tableEntity.getComments());
        map.put("pk", tableEntity.getPk());
        map.put("className", tableEntity.getClassName());
        map.put("classname", tableEntity.getClassname());
        map.put("pathName", tableEntity.getClassname().toLowerCase());
        map.put("columns", tableEntity.getColumns());
        map.put("hasBigDecimal", hasBigDecimal);
        map.put("hasList", hasList);
        map.put("mainPath", mainPath);
        map.put("package", config.getString("package"));
        map.put("moduleName", config.getString("moduleName"));
        map.put("author", config.getString("author"));
        map.put("email", config.getString("email"));
        map.put("datetime", DateUtils.format(new Date(), DateUtils.DATE_TIME_PATTERN));
        VelocityContext context = new VelocityContext(map);

        //èŽ·å–æ¨¡æ¿åˆ—è¡¨
        List<String> templates = getTemplates();
        for (String template : templates) {
            //æ¸²æŸ“æ¨¡æ¿
            StringWriter sw = new StringWriter();
            Template tpl = Velocity.getTemplate(template, "UTF-8");
            tpl.merge(context, sw);

            try {
                //æ·»åŠ åˆ°zip
                zip.putNextEntry(new ZipEntry(getFileName(template, tableEntity.getClassName(), config.getString("package"), config.getString("moduleName"))));
                IOUtils.write(sw.toString(), zip, "UTF-8");
                IOUtils.closeQuietly(sw);
                zip.closeEntry();
            } catch (IOException e) {
                throw new RRException("æ¸²æŸ“æ¨¡æ¿å¤±è´¥ï¼Œè¡¨åï¼š" + tableEntity.getTableName(), e);
            }
        }
    }

    /**
     * ç”Ÿæˆmongoå…¶ä»–å®žä½“ç±»çš„ä»£ç 
     */
    public static void generatorMongoCode(String[] tableNames, ZipOutputStream zip) {
        for (String tableName : tableNames) {
            MongoDefinition info = MongoManager.getInfo(tableName);
            currentTableName = tableName;
            List<MongoGeneratorEntity> childrenInfo = info.getChildrenInfo(tableName);
            childrenInfo.remove(0);
            for (MongoGeneratorEntity mongoGeneratorEntity : childrenInfo) {
                generatorChildrenBeanCode(mongoGeneratorEntity, zip);
            }
        }
    }

    private static void generatorChildrenBeanCode(MongoGeneratorEntity mongoGeneratorEntity, ZipOutputStream zip) {
        //é…ç½®ä¿¡æ¯
        Configuration config = getConfig();
        boolean hasList = false;
        //è¡¨ä¿¡æ¯
        TableEntity tableEntity = mongoGeneratorEntity.toTableEntity();
        //è¡¨åè½¬æ¢æˆJavaç±»å
        String className = tableToJava(tableEntity.getTableName(), config.getStringArray("tablePrefix"));
        tableEntity.setClassName(className);
        tableEntity.setClassname(StringUtils.uncapitalize(className));
        //åˆ—ä¿¡æ¯
        List<ColumnEntity> columsList = new ArrayList<>();
        for (Map<String, String> column : mongoGeneratorEntity.getColumns()) {
            ColumnEntity columnEntity = new ColumnEntity();
            String columnName = column.get("columnName");
            if (columnName.contains(".")) {
                columnName = columnName.substring(columnName.lastIndexOf(".") + 1);
            }
            columnEntity.setColumnName(columnName);
            columnEntity.setDataType(column.get("dataType"));
            columnEntity.setExtra(column.get("extra"));

            //åˆ—åè½¬æ¢æˆJavaå±žæ€§å
            String attrName = columnToJava(columnEntity.getColumnName());
            columnEntity.setAttrName(attrName);
            columnEntity.setAttrname(StringUtils.uncapitalize(attrName));

            //åˆ—çš„æ•°æ®ç±»åž‹ï¼Œè½¬æ¢æˆJavaç±»åž‹
            String attrType = config.getString(columnEntity.getDataType(), columnToJava(columnEntity.getDataType()));
            columnEntity.setAttrType(attrType);

            if (!hasList && "array".equals(columnEntity.getExtra())) {
                hasList = true;
            }
            columsList.add(columnEntity);
        }
        tableEntity.setColumns(columsList);

        //è®¾ç½®velocityèµ„æºåŠ è½½å™¨
        Properties prop = new Properties();
        prop.put("file.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        Velocity.init(prop);
        String mainPath = config.getString("mainPath");
        mainPath = StringUtils.isBlank(mainPath) ? "io.renren" : mainPath;
        //å°è£…æ¨¡æ¿æ•°æ®
        Map<String, Object> map = new HashMap<>();
        map.put("tableName", tableEntity.getTableName());
        map.put("comments", tableEntity.getComments());
        map.put("pk", tableEntity.getPk());
        map.put("className", tableEntity.getClassName());
        map.put("classname", tableEntity.getClassname());
        map.put("pathName", tableEntity.getClassname().toLowerCase());
        map.put("columns", tableEntity.getColumns());
        map.put("hasList", hasList);
        map.put("mainPath", mainPath);
        map.put("package", config.getString("package"));
        map.put("moduleName", config.getString("moduleName"));
        map.put("author", config.getString("author"));
        map.put("email", config.getString("email"));
        map.put("datetime", DateUtils.format(new Date(), DateUtils.DATE_TIME_PATTERN));
        VelocityContext context = new VelocityContext(map);

        //èŽ·å–æ¨¡æ¿åˆ—è¡¨
        List<String> templates = getMongoChildTemplates();
        for (String template : templates) {
            //æ¸²æŸ“æ¨¡æ¿
            StringWriter sw = new StringWriter();
            Template tpl = Velocity.getTemplate(template, "UTF-8");
            tpl.merge(context, sw);
            try {
                //æ·»åŠ åˆ°zip
                zip.putNextEntry(new ZipEntry(getFileName(template, tableEntity.getClassName(), config.getString("package"), config.getString("moduleName"))));
                IOUtils.write(sw.toString(), zip, "UTF-8");
                IOUtils.closeQuietly(sw);
                zip.closeEntry();
            } catch (IOException e) {
                throw new RRException("æ¸²æŸ“æ¨¡æ¿å¤±è´¥ï¼Œè¡¨åï¼š" + tableEntity.getTableName(), e);
            }
        }

    }

    /**
     * åˆ—åè½¬æ¢æˆJavaå±žæ€§å
     */
    public static String columnToJava(String columnName) {
        return WordUtils.capitalizeFully(columnName, new char[]{'_'}).replace("_", "");
    }

    /**
     * è¡¨åè½¬æ¢æˆJavaç±»å
     */
    public static String tableToJava(String tableName, String[] tablePrefixArray) {
        if (null != tablePrefixArray && tablePrefixArray.length > 0) {
            for (String tablePrefix : tablePrefixArray) {
                  if (tableName.startsWith(tablePrefix)){
                    tableName = tableName.replaceFirst(tablePrefix, "");
                }
            }
        }
        return columnToJava(tableName);
    }

    /**
     * èŽ·å–é…ç½®ä¿¡æ¯
     */
    public static Configuration getConfig() {
        try {
            return new PropertiesConfiguration("generator.properties");
        } catch (ConfigurationException e) {
            throw new RRException("èŽ·å–é…ç½®æ–‡ä»¶å¤±è´¥ï¼Œ", e);
        }
    }

    /**
     * èŽ·å–æ–‡ä»¶å
     */
    public static String getFileName(String template, String className, String packageName, String moduleName) {
        String packagePath = "main" + File.separator + "java" + File.separator;
        if (StringUtils.isNotBlank(packageName)) {
            packagePath += packageName.replace(".", File.separator) + File.separator + moduleName + File.separator;
        }
        if (template.contains("MongoChildrenEntity.java.vm")) {
            return packagePath + "entity" + File.separator + "inner" + File.separator + currentTableName+ File.separator + splitInnerName(className)+ "InnerEntity.java";
        }
        if (template.contains("Entity.java.vm") || template.contains("MongoEntity.java.vm")) {
            return packagePath + "entity" + File.separator + className + "Entity.java";
        }

        if (template.contains("Dao.java.vm")) {
            return packagePath + "dao" + File.separator + className + "Dao.java";
        }

        if (template.contains("Service.java.vm")) {
            return packagePath + "service" + File.separator + className + "Service.java";
        }

        if (template.contains("ServiceImpl.java.vm")) {
            return packagePath + "service" + File.separator + "impl" + File.separator + className + "ServiceImpl.java";
        }

        if (template.contains("Controller.java.vm")) {
            return packagePath + "controller" + File.separator + className + "Controller.java";
        }

        if (template.contains("Dao.xml.vm")) {
            return "main" + File.separator + "resources" + File.separator + "mapper" + File.separator + moduleName + File.separator + className + "Dao.xml";
        }

        if (template.contains("menu.sql.vm")) {
            return className.toLowerCase() + "_menu.sql";
        }

        if (template.contains("index.vue.vm")) {
            return "main" + File.separator + "resources" + File.separator + "src" + File.separator + "views" + File.separator + "modules" +
                    File.separator + moduleName + File.separator + className.toLowerCase() + ".vue";
        }

        if (template.contains("add-or-update.vue.vm")) {
            return "main" + File.separator + "resources" + File.separator + "src" + File.separator + "views" + File.separator + "modules" +
                    File.separator + moduleName + File.separator + className.toLowerCase() + "-add-or-update.vue";
        }

        return null;
    }

    private static String splitInnerName(String name){
          name = name.replaceAll("\\.","_");
          return name;
    }
}
