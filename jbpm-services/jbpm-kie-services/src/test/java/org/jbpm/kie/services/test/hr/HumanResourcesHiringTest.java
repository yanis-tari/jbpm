/*
 * Copyright 2013 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.kie.services.test.hr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jbpm.kie.services.api.RuntimeDataService;
import org.jbpm.kie.services.impl.audit.ServicesAwareAuditEventBuilder;
import org.jbpm.kie.services.impl.model.NodeInstanceDesc;
import org.jbpm.kie.services.test.TestIdentityProvider;
import org.jbpm.process.audit.AbstractAuditLogger;
import org.jbpm.process.audit.AuditLoggerFactory;
import org.jbpm.runtime.manager.impl.RuntimeEnvironmentBuilder;
import org.jbpm.runtime.manager.impl.cdi.InjectableRegisterableItemsFactory;
import org.jbpm.runtime.manager.util.TestUtil;
import org.jbpm.services.task.utils.ContentMarshallerHelper;
import org.jbpm.test.util.AbstractBaseTest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.Context;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Content;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.runtime.manager.RuntimeManagerFactory;
import org.kie.internal.runtime.manager.context.EmptyContext;
import org.kie.internal.task.api.InternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bitronix.tm.resource.jdbc.PoolingDataSource;

import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;

/**
 *
 * @author salaboy
 */
@RunWith(Arquillian.class)
public class HumanResourcesHiringTest extends AbstractBaseTest {
    
    private static final Logger logger = LoggerFactory.getLogger(HumanResourcesHiringTest.class);

    @Deployment()
    public static Archive<?> createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, "domain-services.jar")
                .addPackage("org.jboss.seam.transaction") //seam-persistence
                .addPackage("org.jbpm.services.task")
                .addPackage("org.jbpm.services.task.wih") // work items org.jbpm.services.task.wih
                .addPackage("org.jbpm.services.task.annotations")
                .addPackage("org.jbpm.services.task.api")
                .addPackage("org.jbpm.services.task.impl")
                .addPackage("org.jbpm.services.task.events")
                .addPackage("org.jbpm.services.task.exception")
                .addPackage("org.jbpm.services.task.identity")
                .addPackage("org.jbpm.services.task.factories")
                .addPackage("org.jbpm.services.task.internals")
                .addPackage("org.jbpm.services.task.internals.lifecycle")
                .addPackage("org.jbpm.services.task.lifecycle.listeners")
                .addPackage("org.jbpm.services.task.query")
                .addPackage("org.jbpm.services.task.util")
                .addPackage("org.jbpm.services.task.commands") // This should not be required here
                .addPackage("org.jbpm.services.task.deadlines") // deadlines
                .addPackage("org.jbpm.services.task.deadlines.notifications.impl")
                .addPackage("org.jbpm.services.task.subtask")
                .addPackage("org.jbpm.services.task.rule")
                .addPackage("org.jbpm.services.task.rule.impl")
                .addPackage("org.kie.internal.runtime")
                .addPackage("org.kie.internal.runtime.manager")
                .addPackage("org.kie.internal.runtime.manager.cdi.qualifier")
                .addPackage("org.jbpm.runtime.manager")
                .addPackage("org.jbpm.runtime.manager.impl")
                .addPackage("org.jbpm.runtime.manager.impl.cdi")
                .addPackage("org.jbpm.runtime.manager.impl.cdi.qualifier")
                .addPackage("org.jbpm.runtime.manager.impl.context")
                .addPackage("org.jbpm.runtime.manager.impl.factory")
                .addPackage("org.jbpm.runtime.manager.impl.jpa")
                .addPackage("org.jbpm.runtime.manager.impl.manager")
                .addPackage("org.jbpm.runtime.manager.mapper")
                .addPackage("org.jbpm.runtime.manager.impl.task")
                .addPackage("org.jbpm.runtime.manager.impl.tx")
                .addPackage("org.jbpm.shared.services.api")
                .addPackage("org.jbpm.shared.services.impl")
                .addPackage("org.jbpm.kie.services.api")
                .addPackage("org.jbpm.kie.services.impl")
                .addPackage("org.jbpm.kie.services.api.bpmn2")
                .addPackage("org.jbpm.kie.services.impl.bpmn2")
                .addPackage("org.jbpm.kie.services.impl.event.listeners")
                .addPackage("org.jbpm.kie.services.impl.audit")
                .addPackage("org.jbpm.kie.services.impl.util")
                .addPackage("org.jbpm.kie.services.impl.vfs")
                .addPackage("org.jbpm.kie.services.impl.example")
                .addPackage("org.kie.commons.java.nio.fs.jgit")
                .addPackage("org.jbpm.kie.services.test") // Identity Provider Test Impl here
                .addAsResource("jndi.properties", "jndi.properties")
                .addAsManifestResource("META-INF/persistence.xml", ArchivePaths.create("persistence.xml"))
                //                .addAsManifestResource("META-INF/Taskorm.xml", ArchivePaths.create("Taskorm.xml"))
                .addAsManifestResource("META-INF/beans.xml", ArchivePaths.create("beans.xml"));

    }
    private static PoolingDataSource pds;

    @BeforeClass
    public static void setup() {
        TestUtil.cleanupSingletonSessionId();
        pds = TestUtil.setupPoolingDataSource();


    }

    @AfterClass
    public static void teardown() {
        pds.close();
    }

    @After
    public void tearDownTest() {
    }
    
    /*
     * end of initialization code, tests start here
     */
    @Inject
    private EntityManagerFactory emf;
    @Inject
    private BeanManager beanManager;
    @Inject
    private RuntimeDataService runtimeDataService;

    @Test
    public void simpleExecutionTest() {
        assertNotNull(managerFactory);
        String id = "custom-manager";
        AbstractAuditLogger auditLogger =AuditLoggerFactory.newJPAInstance(emf);
        ServicesAwareAuditEventBuilder auditEventBuilder = new ServicesAwareAuditEventBuilder();
        auditEventBuilder.setIdentityProvider(new TestIdentityProvider());
        auditEventBuilder.setDeploymentUnitId(id);
        auditLogger.setBuilder(auditEventBuilder);
        RuntimeEnvironmentBuilder builder = RuntimeEnvironmentBuilder.getDefault()
                .entityManagerFactory(emf)
                .registerableItemsFactory(InjectableRegisterableItemsFactory.getFactory(beanManager, auditLogger));

        builder.addAsset(ResourceFactory.newClassPathResource("repo/processes/hr/hiring.bpmn2"), ResourceType.BPMN2);


        RuntimeManager manager = managerFactory.newSingletonRuntimeManager(builder.get(), id);
        testHiringProcess(manager, EmptyContext.get());

        manager.close();

    }
    @Inject
    private RuntimeManagerFactory managerFactory;

    private void testHiringProcess(RuntimeManager manager, Context context) {

         RuntimeEngine runtime = manager.getRuntimeEngine(context);
        KieSession ksession = runtime.getKieSession();
        TaskService taskService = runtime.getTaskService();


        assertNotNull(runtime);
        assertNotNull(ksession);

        ksession.getWorkItemManager().registerWorkItemHandler("EmailService", new WorkItemHandler() {

             @Override
             public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
                 manager.completeWorkItem(workItem.getId(), null);
             }

             @Override
             public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
                 throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
             }
         });
        ksession.getWorkItemManager().registerWorkItemHandler("TwitterService", new WorkItemHandler() {

             @Override
             public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
                 manager.completeWorkItem(workItem.getId(), null);
             }

             @Override
             public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
                 throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
             }
         });
        
        
        ProcessInstance processInstance = ksession.startProcess("hiring");

        Collection<NodeInstanceDesc> activeNodes = runtimeDataService.getProcessInstanceActiveNodes(
        															manager.getIdentifier(), processInstance.getId());
        assertNotNull(activeNodes);
        assertEquals(1, activeNodes.size());
        
        Collection<NodeInstanceDesc> completedNodes = runtimeDataService.getProcessInstanceCompletedNodes(
        															manager.getIdentifier(), processInstance.getId());
        assertNotNull(completedNodes);
        assertEquals(1, completedNodes.size());
        
        List<TaskSummary> tasks = ((InternalTaskService) taskService).getTasksAssignedByGroup("HR", "en-UK");

        TaskSummary HRInterview = tasks.get(0);

        taskService.claim(HRInterview.getId(), "katy");

        taskService.start(HRInterview.getId(), "katy");

        Map<String, Object> hrOutput = new HashMap<String, Object>();
        hrOutput.put("out_name", "salaboy");
        hrOutput.put("out_age", 29);
        hrOutput.put("out_mail", "salaboy@gmail.com");
        hrOutput.put("out_score", 8);

        taskService.complete(HRInterview.getId(), "katy", hrOutput);
        
        activeNodes = runtimeDataService.getProcessInstanceActiveNodes(
						manager.getIdentifier(), processInstance.getId());
		assertNotNull(activeNodes);
		assertEquals(1, activeNodes.size());
		
		completedNodes = runtimeDataService.getProcessInstanceCompletedNodes(
						manager.getIdentifier(), processInstance.getId());
		assertNotNull(completedNodes);
		assertEquals(2, completedNodes.size());


        assertNotNull(processInstance);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        tasks = ((InternalTaskService) taskService).getTasksAssignedByGroup("IT", "en-UK");
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        TaskSummary techInterview = tasks.get(0);
        Task techInterviewTask = taskService.getTaskById(techInterview.getId());
        Content contentById = taskService.getContentById(techInterviewTask.getTaskData().getDocumentContentId());
        assertNotNull(contentById);

        Map<String, Object> taskContent = (Map<String, Object>) ContentMarshallerHelper.unmarshall(contentById.getContent(), null);

        assertEquals(7, taskContent.size());

        assertEquals("salaboy@gmail.com", taskContent.get("in_mail"));
        assertEquals(29, taskContent.get("in_age"));
        assertEquals("salaboy", taskContent.get("in_name"));

        taskService.claim(techInterview.getId(), "salaboy");

        taskService.start(techInterview.getId(), "salaboy");


        Map<String, Object> techOutput = new HashMap<String, Object>();
        techOutput.put("out_skills", "java, jbpm, drools");
        techOutput.put("out_twitter", "@salaboy");
        techOutput.put("out_score", 8);

        taskService.complete(techInterview.getId(), "salaboy", techOutput);
        
        activeNodes = runtimeDataService.getProcessInstanceActiveNodes(
						manager.getIdentifier(), processInstance.getId());
		assertNotNull(activeNodes);
		assertEquals(1, activeNodes.size());
		
		completedNodes = runtimeDataService.getProcessInstanceCompletedNodes(
						manager.getIdentifier(), processInstance.getId());
		assertNotNull(completedNodes);
		assertEquals(3, completedNodes.size());


        tasks = ((InternalTaskService) taskService).getTasksAssignedByGroup("Accounting", "en-UK");
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        TaskSummary createProposal = tasks.get(0);

        Task createProposalTask = taskService.getTaskById(createProposal.getId());
        contentById = taskService.getContentById(createProposalTask.getTaskData().getDocumentContentId());
        assertNotNull(contentById);
        taskContent = (Map<String, Object>) ContentMarshallerHelper.unmarshall(contentById.getContent(), null);

        assertEquals(6, taskContent.size());

        assertEquals(8, taskContent.get("in_tech_score"));
        assertEquals(8, taskContent.get("in_hr_score"));


        taskService.claim(createProposal.getId(), "john");

        taskService.start(createProposal.getId(), "john");

        Map<String, Object> proposalOutput = new HashMap<String, Object>();
        proposalOutput.put("out_offering", 10000);


        taskService.complete(createProposal.getId(), "john", proposalOutput);
        
        activeNodes = runtimeDataService.getProcessInstanceActiveNodes(
						manager.getIdentifier(), processInstance.getId());
		assertNotNull(activeNodes);
		assertEquals(1, activeNodes.size());
		
		completedNodes = runtimeDataService.getProcessInstanceCompletedNodes(
						manager.getIdentifier(), processInstance.getId());
		assertNotNull(completedNodes);
		assertEquals(5, completedNodes.size());

        tasks = ((InternalTaskService) taskService).getTasksAssignedByGroup("HR", "en-UK");
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        TaskSummary signContract = tasks.get(0);

        Task signContractTask = taskService.getTaskById(signContract.getId());
        contentById = taskService.getContentById(signContractTask.getTaskData().getDocumentContentId());
        assertNotNull(contentById);
        taskContent = (Map<String, Object>) ContentMarshallerHelper.unmarshall(contentById.getContent(), null);

        assertEquals(6, taskContent.size());

        assertEquals(10000, taskContent.get("in_offering"));
        assertEquals("salaboy", taskContent.get("in_name"));

        taskService.claim(signContract.getId(), "katy");

        taskService.start(signContract.getId(), "katy");

        Map<String, Object> signOutput = new HashMap<String, Object>();
        signOutput.put("out_signed", true);
        taskService.complete(signContract.getId(), "katy", signOutput);
        
        activeNodes = runtimeDataService.getProcessInstanceActiveNodes(
						manager.getIdentifier(), processInstance.getId());
		assertNotNull(activeNodes);
		assertEquals(0, activeNodes.size());
		
		completedNodes = runtimeDataService.getProcessInstanceCompletedNodes(
						manager.getIdentifier(), processInstance.getId());
		assertNotNull(completedNodes);
		assertEquals(8, completedNodes.size());

        
        int removeAllTasks = ((InternalTaskService) taskService).removeAllTasks();
        logger.debug(">>> Removed Tasks > {}", removeAllTasks);

    }
}
