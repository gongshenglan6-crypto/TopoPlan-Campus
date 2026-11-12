package com.agent.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TopologyIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void topologyIsSavedReadAndVersionedPerProjectOwner() throws Exception {
        MockHttpSession owner = register("topology_" + System.nanoTime());
        MvcResult project = mockMvc.perform(post("/api/projects")
                        .with(csrf()).session(owner).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Topology project\",\"type\":\"topo_design\",\"color\":\"#1a73e8\"}"))
                .andExpect(status().isOk()).andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).path("data").path("id").asLong();

        String topology = "{\"nodes\":[{\"id\":\"router-1\",\"x\":120}],\"edges\":[],\"zoom\":1}";
        mockMvc.perform(put("/api/projects/" + projectId + "/topology")
                        .with(csrf()).session(owner).contentType(APPLICATION_JSON).content(topology))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.nodes[0].id").value("router-1"));
        mockMvc.perform(get("/api/projects/" + projectId + "/topology").session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        mockMvc.perform(get("/api/projects/" + projectId + "/topology/versions").session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));

        MockHttpSession another = register("other_topology_" + System.nanoTime());
        mockMvc.perform(get("/api/projects/" + projectId + "/topology").session(another))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_FORBIDDEN"));
    }

    @Test
    void fiveRepresentativeAiTopologyPlansCanBeAppliedWithPublicResources() throws Exception {
        String[] plans = {
                "{\"addNodes\":[{\"id\":\"internet-1\",\"type\":\"internet\",\"resourceId\":\"internet\"},{\"id\":\"firewall-1\",\"type\":\"firewall\",\"resourceId\":\"firewall\"},{\"id\":\"core-1\",\"type\":\"core-switch\",\"resourceId\":\"core-switch\"}],\"addEdges\":[{\"source\":\"internet-1\",\"target\":\"firewall-1\",\"sourcePort\":\"Logical-1\",\"targetPort\":\"GE0/0/0\"},{\"source\":\"firewall-1\",\"target\":\"core-1\",\"sourcePort\":\"GE0/0/1\",\"targetPort\":\"GE0/0/1\"}]}" ,
                "{\"addNodes\":[{\"id\":\"router-1\",\"type\":\"router\",\"resourceKey\":\"router\"},{\"id\":\"core-1\",\"type\":\"core-switch\",\"resourceKey\":\"core-switch\"},{\"id\":\"access-1\",\"type\":\"access-switch\",\"resourceKey\":\"access-switch\"}],\"addEdges\":[{\"source\":\"router-1\",\"target\":\"core-1\",\"sourcePort\":\"GE0/0/0\",\"targetPort\":\"GE0/0/1\"},{\"source\":\"core-1\",\"target\":\"access-1\",\"sourcePort\":\"GE0/0/2\",\"targetPort\":\"GE0/0/1\"}]}" ,
                "{\"addNodes\":[{\"id\":\"core-1\",\"type\":\"core-switch\",\"resourceId\":\"core-switch\"},{\"id\":\"ap-1\",\"type\":\"wireless-ap\",\"resourceId\":\"wireless-ap\"},{\"id\":\"server-1\",\"type\":\"server\",\"resourceId\":\"server\"}],\"addEdges\":[{\"source\":\"core-1\",\"target\":\"ap-1\",\"sourcePort\":\"GE0/0/1\",\"targetPort\":\"ETH0\"},{\"source\":\"core-1\",\"target\":\"server-1\",\"sourcePort\":\"GE0/0/2\",\"targetPort\":\"NIC1\"}]}" ,
                "{\"addNodes\":[{\"id\":\"internet-1\",\"type\":\"internet\",\"resourceKey\":\"internet\"},{\"id\":\"firewall-1\",\"type\":\"firewall\",\"resourceKey\":\"firewall\"},{\"id\":\"server-1\",\"type\":\"server\",\"resourceKey\":\"server\"}],\"addEdges\":[{\"source\":\"internet-1\",\"target\":\"firewall-1\",\"sourcePort\":\"Logical-1\",\"targetPort\":\"GE0/0/0\"},{\"source\":\"firewall-1\",\"target\":\"server-1\",\"sourcePort\":\"GE0/0/1\",\"targetPort\":\"NIC1\"}]}" ,
                "{\"addNodes\":[{\"id\":\"core-a\",\"type\":\"core-switch\",\"resourceId\":\"core-switch\"},{\"id\":\"core-b\",\"type\":\"core-switch\",\"resourceId\":\"core-switch\"},{\"id\":\"access-a\",\"type\":\"access-switch\",\"resourceId\":\"access-switch\"},{\"id\":\"access-b\",\"type\":\"access-switch\",\"resourceId\":\"access-switch\"}],\"addEdges\":[{\"source\":\"core-a\",\"target\":\"core-b\",\"sourcePort\":\"GE0/0/1\",\"targetPort\":\"GE0/0/1\"},{\"source\":\"core-a\",\"target\":\"access-a\",\"sourcePort\":\"GE0/0/2\",\"targetPort\":\"GE0/0/1\"},{\"source\":\"core-b\",\"target\":\"access-b\",\"sourcePort\":\"GE0/0/2\",\"targetPort\":\"GE0/0/1\"}]}"
        };
        int[] expectedNodes = { 3, 3, 3, 3, 4 };
        int[] expectedEdges = { 2, 2, 2, 2, 3 };
        MockHttpSession owner = register("ai_plan_five_projects_" + System.nanoTime());

        for (int index = 0; index < plans.length; index++) {
            MvcResult project = mockMvc.perform(post("/api/projects")
                            .with(csrf()).session(owner).contentType(APPLICATION_JSON)
                            .content("{\"name\":\"AI fixture " + (index + 1) + "\",\"type\":\"topo_design\",\"color\":\"#1a73e8\"}"))
                    .andExpect(status().isOk()).andReturn();
            long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).path("data").path("id").asLong();
            JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/projects/" + projectId + "/topology/apply-ai-plan")
                            .with(csrf()).session(owner).contentType(APPLICATION_JSON)
                            .content("{\"planId\":\"fixture-" + (index + 1) + "\",\"plan\":" + plans[index] + "}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(expectedNodes[index], response.path("data").path("nodes").size(), "fixture " + (index + 1) + " node count");
            assertEquals(expectedEdges[index], response.path("data").path("edges").size(), "fixture " + (index + 1) + " edge count");
        }
    }

    @Test
    void aiPlanCanUpdateAndRemoveExistingConnectionByRealEndpoints() throws Exception {
        MockHttpSession owner = register("endpoint_plan_" + System.nanoTime());
        MvcResult project = mockMvc.perform(post("/api/projects")
                        .with(csrf()).session(owner).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Endpoint plan\",\"type\":\"topo_design\",\"color\":\"#1a73e8\"}"))
                .andExpect(status().isOk()).andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).path("data").path("id").asLong();

        String initialPlan = "{\"addNodes\":[{\"id\":\"router-1\",\"type\":\"router\",\"resourceKey\":\"router\"},{\"id\":\"core-1\",\"type\":\"core-switch\",\"resourceKey\":\"core-switch\"}],\"addEdges\":[{\"id\":\"uplink-1\",\"source\":\"router-1\",\"target\":\"core-1\",\"sourcePort\":\"GE0/0/0\",\"targetPort\":\"GE0/0/1\",\"connectionType\":\"千兆以太网\"}]}";
        mockMvc.perform(post("/api/projects/" + projectId + "/topology/apply-ai-plan")
                        .with(csrf()).session(owner).contentType(APPLICATION_JSON)
                        .content("{\"planId\":\"initial\",\"plan\":" + initialPlan + "}"))
                .andExpect(status().isOk());

        String updatePlan = "{\"updateEdges\":[{\"source\":\"router-1\",\"target\":\"core-1\",\"sourcePort\":\"GE0/0/0\",\"targetPort\":\"GE0/0/1\",\"connectionType\":\"万兆以太网\"}]}";
        mockMvc.perform(post("/api/projects/" + projectId + "/topology/apply-ai-plan")
                        .with(csrf()).session(owner).contentType(APPLICATION_JSON)
                        .content("{\"planId\":\"update\",\"plan\":" + updatePlan + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.edges[0].connectionType").value("万兆以太网"));

        String removalPlan = "{\"allowDestructive\":true,\"removeEdges\":[{\"source\":\"router-1\",\"target\":\"core-1\",\"sourcePort\":\"GE0/0/0\",\"targetPort\":\"GE0/0/1\"}]}";
        mockMvc.perform(post("/api/projects/" + projectId + "/topology/apply-ai-plan")
                        .with(csrf()).session(owner).contentType(APPLICATION_JSON)
                        .content("{\"planId\":\"remove\",\"plan\":" + removalPlan + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.edges.length()").value(0));
    }

    private MockHttpSession register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"avatar\":\"\",\"nickname\":\"Topology User\",\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
