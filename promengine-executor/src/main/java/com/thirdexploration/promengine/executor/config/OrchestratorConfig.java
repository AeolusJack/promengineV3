//package com.thirdexploration.promengine.executor.config;
//
//import com.thirdexploration.promengine.core.MemoryService;
//import com.thirdexploration.promengine.core.ModelGateway;
//import com.thirdexploration.promengine.core.PromptManager;
//import com.thirdexploration.promengine.executor.Orchestrator;
//import com.thirdexploration.promengine.executor.ReactOrchestrator;
//import com.thirdexploration.promengine.executor.SimpleOrchestrator;
//import com.thirdexploration.promengine.executor.ToolExecutor;
//import com.thirdexploration.promengine.executor.tool.registry.ToolRegistry;
//import com.thirdexploration.promengine.memory.config.MemoryRetrievalPolicyProperties;
//import com.thirdexploration.promengine.skill.SkillExecutor;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Primary;
//
//@Configuration
//public class OrchestratorConfig {
//
//
//
//
//    @Bean
//    @Primary
//    @ConditionalOnProperty(name = "promengine.orchestrator.mode", havingValue = "SIMPLE", matchIfMissing = true)
//    public Orchestrator simpleOrchestrator(ModelGateway modelGateway,
//                                           MemoryService memoryService,
//                                           ToolRegistry toolRegistry,
//                                           SkillExecutor skillExecutor,
//                                           MemoryRetrievalPolicyProperties policyProperties) {
//        return new SimpleOrchestrator(modelGateway, memoryService, toolRegistry, skillExecutor, policyProperties);
//    }
//
//    @Bean
//    @ConditionalOnProperty(name = "promengine.orchestrator.mode", havingValue = "REACT")
//    public Orchestrator reactOrchestrator(
//             ChatClient.Builder chatClientBuilder,
//             ToolExecutor toolExecutor,
//              MemoryService memoryService,
//                PromptManager promptManager,
//              OrchestratorProperties properties
//    ) {
//        //todo  REACT 模式暂未完全实现，此处留待后续补充
//        return new ReactOrchestrator(chatClientBuilder,toolExecutor,memoryService,promptManager,properties);
////        throw new UnsupportedOperationException("REACT mode is not yet fully implemented. Please use SIMPLE mode.");
//    }
//}