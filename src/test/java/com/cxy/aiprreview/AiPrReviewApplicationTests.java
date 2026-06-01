package com.cxy.aiprreview;

import com.cxy.aiprreview.app.PrReviewApp;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiPrReviewApplicationTests {

    @Test
    void contextLoads() {
    }
    @Resource
    private PrReviewApp prReviewApp;
    @Test
    void reviewCode() {
        String codeDiff = """
                 package Hash;
                
                 import java.util.HashMap;
                 import java.util.Map;
                
                 public class Test001 {
                     public int[] twoSum(int[] nums, int target) {
                         Map<Integer,Integer> map=new HashMap<>();
                         for(int i=0;i<nums.length;i++){
                             if(map.containsKey(target-nums[i])){
                                 return new int[]{map.get(target-nums[i]),i};
                             }
                             map.put(nums[i],i);
                         }
                         throw new IllegalArgumentException("No two sum solution");
                     }
                 }
                
                """;
        String content = prReviewApp.reviewCode(codeDiff);
        System.out.println(content);
    }
}
