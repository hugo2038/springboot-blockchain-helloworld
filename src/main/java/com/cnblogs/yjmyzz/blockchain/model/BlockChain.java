package com.cnblogs.yjmyzz.blockchain.model;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.cnblogs.yjmyzz.blockchain.utils.SHAUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.apache.commons.collections4.MapUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

@Data
@ApiModel(description = "区块链")
public class BlockChain {

    @ApiModelProperty(value = "当前交易列表", dataType = "List<Transaction>")
    @JSONField(serialize = false)
    @JsonIgnore
    private List<Transaction> currentTransactions;

    @ApiModelProperty(value = "所有交易列表", dataType = "List<Transaction>")
    private List<Transaction> transactions;

    @ApiModelProperty(value = "区块列表", dataType = "List<BlockChain>")
    @JSONField(serialize = false)
    @JsonIgnore
    private List<BlockChain> chain;

    @ApiModelProperty(value = "集群的节点列表", dataType = "Set<String>")
    @JSONField(serialize = false)
    @JsonIgnore
    private Set<String> nodes;

    @ApiModelProperty(value = "上一个区块的哈希值", dataType = "String", example = "f461ac428043f328309da7cac33803206cea9912f0d4e8d8cf2786d21e5ff403")
    private String previousHash = "";

    @ApiModelProperty(value = "工作量证明", dataType = "Integer", example = "100")
    private Integer proof = 0;

    @ApiModelProperty(value = "当前区块的索引序号", dataType = "Long", example = "2")
    private Long index = 0L;

    @ApiModelProperty(value = "当前区块的时间戳", dataType = "Long", example = "1526458171000")
    private Long timestamp = 0L;

    @ApiModelProperty(value = "当前区块的哈希值", dataType = "String", example = "g451ac428043f328309da7cac33803206cea9912f0d4e8d8cf2786d21e5ff401")
    private String hash;

    public BlockChain() {
        currentTransactions = new ArrayList<>();
        chain = new ArrayList<>();
        transactions = new ArrayList<>();
        nodes = new HashSet<>();
    }

    public String getHash() {
        return genHash(this);
    }

    private String genHash(BlockChain blockChain) {
        String json = JSON.toJSONString(blockChain.getCurrentTransactions()) +
                JSON.toJSONString(blockChain.getTransactions()) +
                JSON.toJSONString(blockChain.getChain()) +
                blockChain.getPreviousHash() + blockChain.getProof() + blockChain.getIndex() + blockChain.getTimestamp();
        hash = SHAUtils.getSHA256Str(json);
        return hash;
    }

    @JSONField(serialize = false)
    @JsonIgnore
    public BlockChain getLastBlock() {
        if (CollectionUtils.isEmpty(chain)) {
            return null;
        }
        return chain.get(chain.size() - 1);
    }

    public void registerNode(String address) {
        nodes.add(address);
    }

    public BlockChain newBlock(Integer proof, String previousHash) {
        BlockChain block = new BlockChain();
        block.index = chain.size() + 1L;
        block.timestamp = System.currentTimeMillis();
        block.transactions.addAll(currentTransactions);
        block.proof = proof;
        block.previousHash = previousHash;
        currentTransactions.clear();
        chain.add(block);
        return block;
    }

    public Long newTransaction(String sender, String recipient, BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.setSender(sender);
        transaction.setRecepient(recipient);
        transaction.setAmount(amount);
        currentTransactions.add(transaction);
        return getLastBlock().index + 1;
    }

    public Integer proofOfWork(Integer lastProof) {
        int proof = 0;
        while (!validProof(lastProof, proof)) {
            proof += 1;
        }
        return proof;
    }

    public Boolean validProof(Integer lastProof, Integer proof) {
        String guessHash = SHAUtils.getSHA256Str(String.format("{%d}{%d}", lastProof, proof));
        System.out.println(guessHash);
        return guessHash.startsWith("0000");
    }

    public void newSeedBlock() {
        newBlock(100, "1");
    }


    private boolean validChain(List<BlockChain> chain) {
        if (CollectionUtils.isEmpty(chain)) {
            return false;
        }

        BlockChain lastBlock = chain.get(0);
        int currentIndex = 1;
        while (currentIndex < chain.size()) {
            BlockChain block = chain.get(currentIndex);
            if (block.getPreviousHash().equals(genHash(lastBlock))) {
                return false;
            }

            if (validProof(lastBlock.getProof(), block.getProof())) {
                return false;
            }
            currentIndex += 1;
        }
        return true;
    }

    public boolean resolveConflicts() {
        int maxLength = getChain().size();
        List<BlockChain> newChain = new ArrayList<>();
        for (String node : getNodes()) {
            RestTemplate template = new RestTemplate();
            Map map = template.getForObject(node, Map.class);
            int length = MapUtils.getInteger(map, "length");
            List<BlockChain> chain = JSON.parseObject(JSON.toJSONString(MapUtils.getObject(map, "chain")), List.class);
            if (length > maxLength && validChain(chain)) {
                maxLength = length;
                newChain = chain;
            }
        }
        if (!CollectionUtils.isEmpty(newChain)) {
            this.chain = newChain;
            return true;
        }
        return false;
    }

//    public static void main(String[] args) {
//        BlockChain chain = new BlockChain();
//        chain.newSeedBlock();
//        System.out.println(JSON.toJSONString(chain, true));
////        System.out.println(chain.validProof(100L, 1L));
//    }

}
