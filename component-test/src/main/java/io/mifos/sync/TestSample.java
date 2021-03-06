/*
 * Copyright 2017 The Mifos Initiative.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.mifos.sync;

import io.mifos.anubis.test.v1.TenantApplicationSecurityEnvironmentTestRule;
import io.mifos.core.api.context.AutoUserContext;
import io.mifos.core.test.fixture.TenantDataStoreContextTestRule;
import io.mifos.core.test.listener.EnableEventRecording;
import io.mifos.core.test.listener.EventRecorder;
import io.mifos.template.api.v1.events.EventConstants;
import io.mifos.template.api.v1.client.TemplateManager;
import io.mifos.template.api.v1.domain.Sample;
import io.mifos.template.service.TemplateConfiguration;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class TestSample extends SuiteTestEnvironment {
  private static final String LOGGER_NAME = "test-logger";
  private static final String TEST_USER = "homer";


  @Configuration
  @EnableEventRecording
  @EnableFeignClients(basePackages = {"io.mifos.template.api.v1.client"})
  @RibbonClient(name = APP_NAME)
  @Import({TemplateConfiguration.class})
  @ComponentScan("io.mifos.template.listener")
  public static class TestConfiguration {
    public TestConfiguration() {
      super();
    }

    @Bean(name = LOGGER_NAME)
    public Logger logger() {
      return LoggerFactory.getLogger(LOGGER_NAME);
    }
  }

  @ClassRule
  public final static TenantDataStoreContextTestRule tenantDataStoreContext = TenantDataStoreContextTestRule.forRandomTenantName(cassandraInitializer, mariaDBInitializer);

  @Rule
  public final TenantApplicationSecurityEnvironmentTestRule tenantApplicationSecurityEnvironment
          = new TenantApplicationSecurityEnvironmentTestRule(testEnvironment, this::waitForInitialize);

  private AutoUserContext userContext;

  @Autowired
  private TemplateManager testSubject;

  @Autowired
  private EventRecorder eventRecorder;

  @SuppressWarnings("WeakerAccess")
  @Autowired
  @Qualifier(LOGGER_NAME)
  Logger logger;

  public TestSample() {
    super();
  }

  @Before
  public void prepTest() {
    userContext = tenantApplicationSecurityEnvironment.createAutoUserContext(TestSample.TEST_USER);
  }

  @After
  public void cleanTest() {
    userContext.close();
    eventRecorder.clear();
  }

  public boolean waitForInitialize() {
    try {
      return this.eventRecorder.wait(EventConstants.INITIALIZE, APP_VERSION);
    } catch (final InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  public void shouldCreateSample() throws InterruptedException {
    logger.info("Running test shouldCreateSample.");
    final Sample sample = Sample.create(RandomStringUtils.randomAlphanumeric(8), RandomStringUtils.randomAlphanumeric(512));
    this.testSubject.createEntity(sample);

    Assert.assertTrue(this.eventRecorder.wait(EventConstants.POST_SAMPLE, sample.getIdentifier()));

    final Sample createdSample = this.testSubject.getEntity(sample.getIdentifier());
    Assert.assertEquals(sample, createdSample);
  }

  @Test
  public void shouldListSamples() {
    logger.info("Running test shouldListSamples.");
    final List<Sample> allEntities = this.testSubject.findAllEntities();
    Assert.assertNotNull(allEntities);
  }
}
