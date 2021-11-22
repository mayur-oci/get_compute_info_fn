package com.oci.fn;

import com.fnproject.fn.api.InvocationContext;
import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.*;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.VirtualNetworkClient;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.VnicAttachment;
import com.oracle.bmc.core.requests.GetVnicRequest;
import com.oracle.bmc.core.requests.ListInstancesRequest;
import com.oracle.bmc.core.requests.ListVnicAttachmentsRequest;
import com.oracle.bmc.core.responses.GetVnicResponse;
import com.oracle.bmc.core.responses.ListInstancesResponse;
import com.oracle.bmc.core.responses.ListVnicAttachmentsResponse;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class OciComputeInfoCollector {

    private ComputeClient computeClient = null;
    private VirtualNetworkClient networkClient = null;

    private BasicAuthenticationDetailsProvider getProvider() throws IOException {
        final ConfigFileReader.ConfigFile configFile = ConfigFileReader.parseDefault();
        final BasicAuthenticationDetailsProvider provider = new ConfigFileAuthenticationDetailsProvider(configFile);
        return provider;
    }

    private BasicAuthenticationDetailsProvider getProviderFn() throws IOException {
        final String TMP_CONFIG_TXT = "/oci/config.txt";
        File configFile = new File(TMP_CONFIG_TXT);

        if (!configFile.exists()) {
            System.out.println("configFile File truly does not exists!!!");
        }

        final String TMP_PVT_KEY = "/oci/oci_api_key.pem";
        File pvtKey = new File(TMP_PVT_KEY);

        if (!pvtKey.exists()) {
            System.out.println("pvtKeyFile truly does not exists!!!");
        }

        FileInputStream input = new FileInputStream(configFile);
        ConfigFileReader.ConfigFile config = ConfigFileReader.parse((InputStream)input,"DEFAULT");
        final BasicAuthenticationDetailsProvider provider = new ConfigFileAuthenticationDetailsProvider(config);
        return provider;
    }


    private BasicAuthenticationDetailsProvider getInstancePriProvider() throws IOException {
        final BasicAuthenticationDetailsProvider provider = (AuthenticationDetailsProvider) new InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder().build();
        return provider;
    }



    private BasicAuthenticationDetailsProvider getResourcePriProvider() throws IOException {
        final BasicAuthenticationDetailsProvider provider = ResourcePrincipalAuthenticationDetailsProvider.builder().build();
        return provider;
    }


    public String getComputeTargets(InvocationContext invocationContext, String input) throws IOException {
        System.out.println("InvocationContext: "+ invocationContext);
        BasicAuthenticationDetailsProvider provider =  getResourcePriProvider();
        computeClient = new ComputeClient(provider);
        computeClient.setRegion(Region.fromRegionCode("us-phoenix-1"));


        String compID = "ocid1.compartment.oc1..aaaaaaaapipdwb47b3nbfmcvp7l7dh6oi5vuu5cpkwtg75s6bra5mpezeuqq";

        String name = (input == null || input.isEmpty()) ? "world"  : input;

        System.out.println("Inside Java Hello World function");
        ListInstancesRequest request = ListInstancesRequest.builder().compartmentId(compID).build();
        ListInstancesResponse instances = computeClient.listInstances(request);
        //System.err.println("ListInstancesResponse " + instances);

        List<Instance> instanceList = instances.getItems();
        System.err.println("No. of compute instances found in compartment " + instanceList.size());
        Map<String, String> names = Collections.emptyMap();
        names = instanceList.stream()
                .filter(instance -> instance.getLifecycleState() == Instance.LifecycleState.Running)
                .collect(Collectors.toMap(Instance::getId, Instance::toString));

        System.err.println("Compute instances " + names.keySet());
        List<JSONObject> jsonObjList = new ArrayList<JSONObject>();

        networkClient = new VirtualNetworkClient(provider);
        networkClient.setRegion(Region.fromRegionCode("us-phoenix-1"));
        System.out.println("network client region is :"+networkClient.getEndpoint());

        for (Instance instance:instanceList){
            String instanceOcid = instance.getId();
            if (instance.getLifecycleState() == Instance.LifecycleState.Running && !instance.getDisplayName().equals("prom_server")){
                ListVnicAttachmentsRequest listVnicRequest = ListVnicAttachmentsRequest.builder()
                        .instanceId(instanceOcid).compartmentId(instance.getCompartmentId()).build();
                System.out.println("cmptid "+instance.getCompartmentId());
                System.out.println("compute client region:"+computeClient.getEndpoint());
                ListVnicAttachmentsResponse listOfVnicAttachments = computeClient.listVnicAttachments(listVnicRequest);
                if (listOfVnicAttachments.get__httpStatusCode__() == 200){
                    for (VnicAttachment vnicAttachment:listOfVnicAttachments.getItems()){
                        if (vnicAttachment.getLifecycleState() == VnicAttachment.LifecycleState.Attached){
                            GetVnicRequest vnicRequest = GetVnicRequest.builder().vnicId(vnicAttachment.getVnicId()).build();
                            GetVnicResponse vnicDetails = networkClient.getVnic(vnicRequest);
                            if (vnicDetails.get__httpStatusCode__() == 200) {
                                Map<String, Object> computeNode = new HashMap<String, Object>();
                                List<String> targetList=new ArrayList<>(); targetList.add(vnicDetails.getVnic().getPrivateIp() + ":9100");
                                computeNode.put("targets", targetList);
                                Map<String, String> computeNodeMetaData = new HashMap<String, String>();
                                computeNodeMetaData.put("AvailabilityDomain",instance.getAvailabilityDomain());
                                computeNodeMetaData.put("Region",instance.getRegion());
                                computeNodeMetaData.put("DisplayName",instance.getDisplayName());
                                computeNodeMetaData.put("CompartmentId",instance.getCompartmentId());
                                computeNodeMetaData.put("Tags",instance.getFreeformTags().toString());
                                computeNode.put("labels", computeNodeMetaData);

                                JSONObject targetEntry = new JSONObject(computeNode);
                                jsonObjList.add(targetEntry);

                                break;
                            }
                        }
                    }
                }
            }
        }

        //System.out.println("Prometheus list:\n"+ JSONArray.toJSONString(jsonObjList));

//        Map<String, String> http_response = new HashMap<>();
//        Map<String, String> headers
//        http_response.put("Headers", )

        invocationContext.setResponseContentType("application/json");

        return JSONArray.toJSONString(jsonObjList);

    }

}