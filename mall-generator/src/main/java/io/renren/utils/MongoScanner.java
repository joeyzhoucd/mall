package io.renren.utils;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoCommandException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import io.renren.config.MongoManager;
import io.renren.entity.mongo.MongoDefinition;
import io.renren.entity.mongo.Type;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

/**
 * @author: gxz  514190950@qq.com
 **/
public class MongoScanner {
    private Logger logger = LoggerFactory.getLogger(getClass());

    private MongoCollection<Document> collection;

    final private int scanCount;

    private List<String> colNames;

    private MongoDefinition mongoDefinition;


    private final static int[] TYPE = {3, 16, 18, 8, 9, 2, 1};

    private final static int ARRAY_TYPE = 4;

    private final static int MAX_COUNT = 200000;

    private final static int DEFAULT_COUNT = 100000;


    public MongoScanner(MongoCollection<Document> collection) {
        this.collection = collection;
        this.scanCount = DEFAULT_COUNT;
        scan();
    }

    private void scan() {
        // åˆå§‹åŒ–
        initColNames();
        // è§£æžå±žæ€§å€¼
        mongoDefinition = scanType();
        MongoManager.putInfo(collection.getNamespace().getCollectionName(), mongoDefinition);
        // è§£æžå®Œæˆä¹‹åŽé‡Šæ”¾é“¾æŽ¥èµ„æº
        this.collection = null;

    }

    public MongoDefinition getProduct() {
        return mongoDefinition;
    }


    /**
     * åŠŸèƒ½æè¿°:åˆ†ç»„å‘é€èšåˆå‡½æ•°(èŽ·å¾—ä¸€çº§å±žæ€§å)
     *
     * @author : gxz
     */
    public List<String> groupAggregation(Integer skip, Integer limit) throws MongoCommandException {
        skip = skip == null ? 0 : skip;
        limit = limit == null ? scanCount : limit;
        MongoCollection<Document> collection = this.collection;
        BasicDBObject $project = new BasicDBObject("$project", new BasicDBObject("arrayofkeyvalue", new BasicDBObject("$objectToArray", "$$ROOT")));
        BasicDBObject $unwind = new BasicDBObject("$unwind", "$arrayofkeyvalue");
        BasicDBObject $skip = new BasicDBObject("$skip", skip);
        BasicDBObject $limit = new BasicDBObject("$limit", limit);
        BasicDBObject filed = new BasicDBObject("_id", "null");
        filed.append("allkeys", new BasicDBObject("$addToSet", "$arrayofkeyvalue.k"));
        BasicDBObject $group = new BasicDBObject("$group", filed);
        List<BasicDBObject> dbStages = Arrays.asList($project, $skip, $limit, $unwind, $group);
        // System.out.println(dbStages);  å‘é€çš„èšåˆå‡½æ•°   èŽ·å¾—æ‰€æœ‰å‚æ•°åç§°
        AggregateIterable<Document> aggregate = collection.aggregate(dbStages);
        Document document = aggregate.first();
        if (document == null) {
            BasicDBObject existsQuery = new BasicDBObject("$ROOT", new BasicDBObject("$exists", true));
            MongoCursor<Document> existsList = collection.find(existsQuery).limit(100).iterator();
            Set<String> keySet = new HashSet<>();
            while (existsList.hasNext()) {
                Document next = existsList.next();
                Map<String, Object> keyMap = (Document) next.get("$ROOT");
                keySet.addAll(keyMap.keySet());
            }
            return new ArrayList<>(keySet);
        } else {
            return (List<String>) document.get("allkeys");
        }

    }


    /**
     * å¦‚æžœä¸€ä¸ªæ–‡æ¡£æ˜¯å¯¹è±¡ç±»åž‹  èŽ·å¾—è¿™ä¸ªå±žæ€§çš„ä¸‹ä¸€çº§çš„å±žæ€§åçš„é›†åˆ
     * ä¾‹å­: user:{name:"å¼ ä¸‰",age:12}  ä¼ å…¥user  è¿”å›ž[name,age]
     *
     * @param parameterName ä¸Šå±‚å‚æ•°å  è¿™ä¸ªå‚æ•°åå¯ä»¥åŒ…å«ä¸€ä¸ªæˆ–å¤šä¸ª.
     *                      æ³¨: å‚æ•°ä¼ é€’ä¹‹å‰éœ€ç¡®è®¤:  1.ä¸Šå±‚å±žæ€§ä¸€å®šæ˜¯å¯¹è±¡ç±»åž‹
     * @return è¿”å›žè¿™ä¸ªå±žæ€§å†…çš„æ‰€æœ‰å±žæ€§å
     */
    public Set<String> getNextParameterNames(String parameterName) {
        Document condition = new Document(parameterName, new Document("$exists", true));
        Document match = new Document("$match", condition);
        String unwindName = parameterName;
        if (parameterName.contains(".")) {
            unwindName = parameterName.split("\\.")[0];
        }
        Document unwind = new Document("$unwind", "$" + unwindName);
        Document limit = new Document("$limit", 3000);
        Document project = new Document("$project", new Document("list", "$" + parameterName).append("_id", false));
        Document unwind2 = new Document("$unwind", "$list");
        AggregateIterable<Document> aggregate = this.collection.aggregate(Arrays.asList(match, unwind, limit, project, unwind2));
        Set<String> names = new HashSet<>();
        for (Document document : aggregate) {
            Object list = document.get("list");
            if (list instanceof Map) {
                Set<String> documentNames = ((Document) list).keySet();
                names.addAll(documentNames);
            }
        }
        logger.info("è§£æž" + parameterName + "æœ‰" + names.size() + "ä¸ªå­å±žæ€§");
        return names;
    }


    /**
     * åŠŸèƒ½æè¿°:æä¾›å±žæ€§å è§£æžå±žæ€§ç±»åž‹
     * èŽ·å–ç›¸åº”çš„å±žæ€§ä¿¡æ¯  å°è£…æˆgeneratorå¯¹è±¡
     *
     * @return : è§£æžä¹‹åŽçš„Model {@see #MongoDefinition}
     * @param: propertyName å±žæ€§å å¯ä»¥æ˜¯å±‚çº§å  æ¯”å¦‚ name ä¹Ÿå¯ä»¥æ˜¯info.name
     * @see MongoDefinition
     */

    public MongoDefinition processNameType(String propertyName) {
        MongoCollection<Document> collection = this.collection;
        MongoDefinition result = new MongoDefinition();
        if ("_id".equals(propertyName)) {
            result.setType(2);
            result.setPropertyName("_id");
            return result;
        }
        result.setPropertyName(propertyName);
        MongoCursor<Document> isArray = collection.find(new Document(propertyName, new Document("$type", ARRAY_TYPE))).limit(1).iterator();
        if (isArray.hasNext()) {
            result.setArray(true);
            for (int i : TYPE) {
                MongoCursor<Document> iterator = collection.find(new Document(propertyName, new Document("$type", i))).limit(1).iterator();
                if (iterator.hasNext()) {
                    if (i == 3) {
                        result.setChild(this.produceChildList(propertyName));
                    }
                    //1æ˜¯double 2æ˜¯string 3æ˜¯å¯¹è±¡ 4æ˜¯æ•°ç»„ 16æ˜¯int 18 æ˜¯long
                    result.setType(i);
                    logger.info("è§£æž[" + propertyName + "]æ˜¯[List][" + Type.typeInfo(result.getType()) + "]");
                    return result;
                }
            }
        } else {
            for (int i : TYPE) {
                MongoCursor<Document> iterator = collection.find(new Document(propertyName, new Document("$type", i))).limit(1).iterator();
                if (iterator.hasNext()) {
                    if (i == 3) {
                        result.setChild(this.produceChildList(propertyName));
                    }
                    //1æ˜¯double 2æ˜¯string 3æ˜¯å¯¹è±¡ 4æ˜¯æ•°ç»„ 16æ˜¯int 18 æ˜¯long
                    //åˆ°è¿™é‡Œå°±æ˜¯æ•°ç»„äº†
                    result.setType(i);
                    logger.info("è§£æž[" + propertyName + "]æ˜¯[" + Type.typeInfo(result.getType()) + "]");
                    return result;
                }
            }
            result.setType(2);
        }
        logger.info("è§£æž[" + propertyName + "]æ˜¯[" + Type.typeInfo(result.getType()) + "]");
        return result;
    }


    private List<MongoDefinition> produceChildList(String parentName) {
        Set<String> nextParameterNames = this.getNextParameterNames(parentName);
        List<String> strings = new ArrayList<>(nextParameterNames);
        List<String> collect = strings.stream().map(name -> parentName + "." + name).collect(Collectors.toList());
        ForkJoinPool pool = new ForkJoinPool();
        ForkJoinTask<List<MongoDefinition>> task = new ForkJoinProcessType(collect);
        return pool.invoke(task);
    }

    private List<String> distinctAndJoin(List<String> a, List<String> b) {
        a.removeAll(b);
        a.addAll(b);
        return a;
    }


    /**
     * åŠŸèƒ½æè¿°:è§£æžè¿™ä¸ªé›†åˆçš„åˆ—å  ç”¨ForkJoinæ¡†æž¶å®žçŽ°
     */
    private void initColNames() {
        long start = System.currentTimeMillis();
        int scan = this.scanCount;
        long count = this.collection.countDocuments();
        ForkJoinPool pool = new ForkJoinPool();
        ForkJoinTask<List<String>> task;
        if (count > (long) scan) {
            task = new ForkJoinGetProcessName(0, scan);
        } else {
            task = new ForkJoinGetProcessName(0, (int) count);
        }
        this.colNames = pool.invoke(task);
        logger.info("collection[" + this.collection.getNamespace().getCollectionName() +
                "]åˆå§‹åŒ–åˆ—åæˆåŠŸ.....     ç”¨æ—¶: " + (System.currentTimeMillis() - start) + "æ¯«ç§’");
    }

    private MongoDefinition scanType() {
        MongoDefinition result = new MongoDefinition();
        List<String> colNames = this.colNames;
        ForkJoinPool pool = new ForkJoinPool();
        ForkJoinTask<List<MongoDefinition>> task = new ForkJoinProcessType(colNames);
        List<MongoDefinition> invoke = pool.invoke(task);
        return result.setChild(invoke).setPropertyName(this.collection.getNamespace().getCollectionName());
    }

    /**
     * åŠŸèƒ½æè¿°:forkJoinå¤šçº¿ç¨‹æ¡†æž¶çš„å®žçŽ°  é€šè¿‡ä¸šåŠ¡æ‹†åˆ†è§£æžç±»åž‹
     */
    class ForkJoinProcessType extends RecursiveTask<List<MongoDefinition>> {
        List<String> names;
        private final int THRESHOLD = 6;

        ForkJoinProcessType(List<String> names) {
            this.names = names;
        }

        @Override
        protected List<MongoDefinition> compute() {
            if (names.size() <= THRESHOLD) {
                List<MongoDefinition> result = new ArrayList<>();
                for (String name : names) {
                    MongoDefinition childrenDefinition = processNameType(name);
                    result.add(childrenDefinition);
                }
                return result;
            } else {
                int size = names.size();
                int middle = size / 2;
                List<String> leftList = names.subList(0, middle);
                List<String> rightList = names.subList(middle, size);
                ForkJoinProcessType pre = new ForkJoinProcessType(leftList);
                pre.fork();
                ForkJoinProcessType next = new ForkJoinProcessType(rightList);
                next.fork();
                return mergeList(pre.join(), next.join());
            }
        }
    }

    /**
     * åŠŸèƒ½æè¿°:forkJoinå¤šçº¿ç¨‹æ¡†æž¶çš„å®žçŽ°  é€šè¿‡ä¸šåŠ¡æ‹†åˆ†èŽ·å¾—å±žæ€§å
     */
    class ForkJoinGetProcessName extends RecursiveTask<List<String>> {
        private int begin; //æŸ¥è¯¢å¼€å§‹ä½ç½®
        private int end;
        private final int THRESHOLD = 5000;

        ForkJoinGetProcessName(int begin, int end) {
            this.begin = begin;
            this.end = end;
        }

        @Override
        protected List<String> compute() {
            int count = end - begin;
            if (THRESHOLD >= count) {
                return groupAggregation(begin, count);
            } else {
                int middle = (begin + end) / 2;
                ForkJoinGetProcessName pre = new ForkJoinGetProcessName(begin, middle);
                pre.fork();
                ForkJoinGetProcessName next = new ForkJoinGetProcessName(middle + 1, end);
                next.fork();
                return distinctAndJoin(pre.join(), next.join()); //åŽ»é‡åˆå¹¶
            }
        }
    }
    public  <T> List<T> mergeList(List<T> list1, List<T> list2){
        list1.addAll(list2);
        return list1;
    }
}
