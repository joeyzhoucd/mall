package io.renren.factory;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import io.renren.config.MongoCondition;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author: gxz gongxuanzhang@foxmail.com
 **/

@Component
@Conditional(MongoCondition.class)
public class MongoDBCollectionFactory {

    private static  final String TABLE_NAME_KEY = "tableName";
    private static final String LIMIT_KEY = "limit";
    private static final String OFFSET_KEY = "offset";

    private static MongoDatabase mongoDatabase;

    // æ­¤å¤„æ˜¯ä¸ºäº†å…¼å®¹mongoç›¸å…³å†…å®¹å’Œå…³ç³»åž‹æ•°æ®åº“çš„é™æ€è€¦åˆæ‰€å¯¼è‡´çš„é—®é¢˜

    @Autowired
    private MongoDatabase database;
    @PostConstruct
    public void initMongoDatabase(){
        mongoDatabase = database;
    }

    /***
     * é€šè¿‡è¡¨åèŽ·å¾—æŸ¥è¯¢å¯¹è±¡
     * @author gxz
     * @date  2020/5/9
     * @param collectionName mongoçš„é›†åˆå(è¡¨å)
     * @return è¿žæŽ¥æŸ¥è¯¢å¯¹è±¡
     **/
    public MongoCollection<Document> getCollection(String collectionName) {
        return mongoDatabase.getCollection(collectionName);
    }

    /***
     * èŽ·å¾—å½“å‰æ•°æ®åº“çš„é›†åˆåç§°
     * æ³¨: mongoç›¸å¯¹å…³ç³»åž‹æ•°æ®åº“è¾ƒä¸ºç‰¹æ®Šï¼ŒæŸ¥è¯¢è¡¨åæ— æ³•åˆ†é¡µï¼Œç”¨streamå®žçŽ°
     * @author gxz
     * @date  2020/5/9
     * @param map è¿™æ˜¯æŸ¥è¯¢æ¡ä»¶ å’Œå…³ç³»åž‹æ•°æ®åº“ä¸€è‡´
     * @return é›†åˆåç§°
     **/
    public static List<String>  getCollectionNames(Map<String, Object> map) {
        int limit = Integer.valueOf(map.get(LIMIT_KEY).toString());
        int skip = Integer.valueOf(map.get(OFFSET_KEY).toString());
        List<String> names;
        if (map.containsKey(TABLE_NAME_KEY)) {
            names = getCollectionNames(map.get(TABLE_NAME_KEY).toString());
        } else {
            names = getCollectionNames();
        }
        return names.stream().skip(skip).limit(limit).collect(Collectors.toList());
    }
    /***
     * èŽ·å¾—é›†åˆåç§°æ€»æ•°(è¡¨çš„æ•°é‡) ä¸ºäº†é€‚é…MyBatisPlusçš„åˆ†é¡µæ’ä»¶ æä¾›æ–¹æ³•
     * @author gxz
     * @date  2020/5/9
     * @param map è¿™æ˜¯æŸ¥è¯¢æ¡ä»¶ å’Œå…³ç³»åž‹æ•°æ®åº“ä¸€è‡´
     * @return int
     **/
    public static int getCollectionTotal(Map<String, Object> map) {
        if (map.containsKey(TABLE_NAME_KEY)) {
            return getCollectionNames(map.get(TABLE_NAME_KEY).toString()).size();
        }
        return getCollectionNames().size();

    }


    private static List<String> getCollectionNames() {
        MongoIterable<String> names = mongoDatabase.listCollectionNames();
        List<String> result = new ArrayList<>();
        for (String name : names) {
            result.add(name);
        }
        return result;
    }

    private static List<String> getCollectionNames(String likeName) {
        return getCollectionNames()
                .stream()
                .filter((name) -> name.contains(likeName)).collect(Collectors.toList());
    }
}
