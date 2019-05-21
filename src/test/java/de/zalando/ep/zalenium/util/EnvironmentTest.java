package de.zalando.ep.zalenium.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;


public class EnvironmentTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void getDoubleArrayEnvVariable() {
        String doubleArrayFormat = "0.5,2.5,5,10,15,20,25,30,35,40,50,60";
        String testSessionLatencyBuckets = "ZALENIUM_TEST_SESSION_LATENCY_BUCKETS";
        environmentVariables.set(testSessionLatencyBuckets, doubleArrayFormat);

        double[] doubleArrayEnvVariable = new Environment().getDoubleArrayEnvVariable(testSessionLatencyBuckets);
        assertArrayEquals(doubleArrayEnvVariable, new double[]{0.5,2.5,5,10,15,20,25,30,35,40,50,60}, 0);
    }

    @Test
    public void getYamlListEnvVariable() throws JsonProcessingException {
        List<Toleration> givenTolerations = new ArrayList<>();

        givenTolerations.add(
            new TolerationBuilder()
                    .withKey("no-cluster-autoscaling")
                    .withOperator("Equal")
                    .withEffect("NoExecute")
                    .withValue("false")
                    .build()
        );
        givenTolerations.add(
            new TolerationBuilder()
                    .withKey("cluster-autoscaling")
                    .withOperator("Equal")
                    .withEffect("NoExecute")
                    .withValue("true")
                    .build()
        );

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        String kubernetesTolerationsEnvVar = "ZALENIUM_KUBERNETES_TOLERATIONS";
        environmentVariables.set(kubernetesTolerationsEnvVar, mapper.writeValueAsString(givenTolerations));

        // example of yaml string for env: "---\n- effect: \"NoExecute\"\n  key: \"no-cluster-autoscaling\"\n  operator: \"Equal\"\n  value: \"false\"\n- effect: \"NoExecute\"\n  key: \"cluster-autoscaling\"\n  operator: \"Equal\"\n  value: \"true\""
        // OR
        //---
        //- effect: "NoExecute"
        //  key: "no-cluster-autoscaling"
        //  operator: "Equal"
        //  value: "false"
        //- effect: "NoExecute"
        //  key: "cluster-autoscaling"
        //  operator: "Equal"
        //  value: "true"

        List<Toleration> tolerationsList = new Environment().getYamlListEnvVariable(kubernetesTolerationsEnvVar, Toleration.class, new ArrayList<Toleration>());
        int i=0;
        for (Toleration toleration : tolerationsList) {
            assertEquals(toleration.getKey(), givenTolerations.get(i).getKey());
            assertEquals(toleration.getOperator(), givenTolerations.get(i).getOperator());
            assertEquals(toleration.getEffect(), givenTolerations.get(i).getEffect());
            assertEquals(toleration.getValue(), givenTolerations.get(i).getValue());
            i++;
        }
    }

    @Test
    public void getMapEnvVariable() {
        Map<String,String> nodeSelector = new HashMap<>();

        nodeSelector.put("beta.kubernetes.io/os", "linux");
        nodeSelector.put("kops.k8s.io/instancegroup", "no-cluster-autoscaling");

        String nodeSelectorAsString = nodeSelector.entrySet()
                .stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));

        String kubernetesNodeSelectorEnvVar = "ZALENIUM_KUBERNETES_NODE_SELECTOR";
        environmentVariables.set(kubernetesNodeSelectorEnvVar, nodeSelectorAsString);

        Map<String, String> nodeSelectorMap = new Environment().getMapEnvVariable(kubernetesNodeSelectorEnvVar, new HashMap<>());
        assertEquals(nodeSelectorMap, nodeSelector);
    }

}