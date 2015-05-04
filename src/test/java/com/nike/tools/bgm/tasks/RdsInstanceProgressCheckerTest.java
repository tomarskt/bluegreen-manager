package com.nike.tools.bgm.tasks;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.amazonaws.services.rds.model.DBInstance;
import com.nike.tools.bgm.client.aws.RdsClient;
import com.nike.tools.bgm.client.aws.RdsInstanceStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RdsInstanceProgressCheckerTest
{
  private static final String LOG_CONTEXT = "(Log Context) ";
  private static final int WAIT_NUM = 1;
  private static final String INSTANCE_ID = "rds-instance-hello";
  private static final String ANOTHER_INSTANCE_ID = "rds-instance-goodbye";
  private static final String STATUS_UNKNOWN = "unknown";

  @Mock
  private RdsClient mockRdsClient;

  private RdsInstanceProgressChecker makeProgressChecker(DBInstance initialInstance, boolean create)
  {
    return new RdsInstanceProgressChecker(INSTANCE_ID, LOG_CONTEXT, mockRdsClient, initialInstance, create);
  }

  /**
   * Test helper - makes a DBInstance
   */
  private DBInstance fakeInstance(String instanceId, RdsInstanceStatus status)
  {
    DBInstance dbInstance = new DBInstance();
    dbInstance.setDBInstanceIdentifier(instanceId);
    dbInstance.setDBInstanceStatus(status == null ? STATUS_UNKNOWN : status.toString());
    return dbInstance;
  }

  private void testGetDescription(String expectedSubstring, boolean create)
  {
    RdsInstanceProgressChecker progressChecker = makeProgressChecker(fakeInstance(INSTANCE_ID, RdsInstanceStatus.AVAILABLE), create);
    assertTrue(progressChecker.getDescription().contains(expectedSubstring));
  }

  @Test
  public void testGetDescriptionBoth()
  {
    testGetDescription("Create", true);
    testGetDescription("Modify", false);
  }

  /**
   * Initial instance with the given acceptable initial status = not done.
   */
  private void testInitialCheck_Acceptable(RdsInstanceStatus acceptableInitialStatus, boolean create)
  {
    RdsInstanceProgressChecker progressChecker = makeProgressChecker(fakeInstance(INSTANCE_ID, acceptableInitialStatus), create);
    progressChecker.initialCheck();
    assertFalse(progressChecker.isDone());
  }

  /**
   * Initially creating = not done.
   */
  @Test
  public void testInitialCheckCreate_Creating()
  {
    testInitialCheck_Acceptable(RdsInstanceStatus.CREATING, true);
  }

  /**
   * Initially creating = not done.
   */
  @Test
  public void testInitialCheckModify_Modifying()
  {
    testInitialCheck_Acceptable(RdsInstanceStatus.MODIFYING, false);
  }

  /**
   * Initially deleting = end with error.
   */
  private void testInitialCheck_Deleting(boolean create)
  {
    RdsInstanceProgressChecker progressChecker = makeProgressChecker(fakeInstance(INSTANCE_ID, RdsInstanceStatus.DELETING), create);
    progressChecker.initialCheck();
    assertTrue(progressChecker.isDone());
    assertNull(progressChecker.getResult());
  }

  @Test
  public void testInitialCheckBoth_Deleting()
  {
    testInitialCheck_Deleting(true);
    testInitialCheck_Deleting(false);
  }

  /**
   * Initially wrong instance id = throw.
   */
  private void testInitialCheck_WrongId(RdsInstanceStatus acceptableInitialStatus, boolean create)
  {
    RdsInstanceProgressChecker progressChecker = makeProgressChecker(fakeInstance(ANOTHER_INSTANCE_ID, acceptableInitialStatus), true);
    progressChecker.initialCheck();
  }

  @Test(expected = IllegalStateException.class)
  public void testInitialCheckCreate_WrongId()
  {
    testInitialCheck_WrongId(RdsInstanceStatus.CREATING, true);
  }

  @Test(expected = IllegalStateException.class)
  public void testInitialCheckModify_WrongId()
  {
    testInitialCheck_WrongId(RdsInstanceStatus.MODIFYING, false);
  }

  /**
   * Initially available = done.
   */
  private void testInitialCheck_Available(boolean create)
  {
    DBInstance initialInstance = fakeInstance(INSTANCE_ID, RdsInstanceStatus.AVAILABLE);
    RdsInstanceProgressChecker progressChecker = makeProgressChecker(initialInstance, create);
    progressChecker.initialCheck();
    assertTrue(progressChecker.isDone());
    assertEquals(initialInstance, progressChecker.getResult());
  }

  @Test
  public void testInitialCheckBoth_Available()
  {
    testInitialCheck_Available(true);
    testInitialCheck_Available(false);
  }

  /**
   * Prepare the mock return value of describeInstance.
   */
  private void whenDescribeInstance(DBInstance dbInstance)
  {
    when(mockRdsClient.describeInstance(INSTANCE_ID)).thenReturn(dbInstance);
  }

  /**
   * Verifies the mock was called.
   */
  private void verifyDescribeInstance()
  {
    verify(mockRdsClient).describeInstance(INSTANCE_ID);
  }


  /**
   * Followup with the given acceptable continuing state = not done.
   */
  private void testFollowupCheck_Acceptable(RdsInstanceStatus acceptableFollowupStatus, boolean create)
  {
    RdsInstanceProgressChecker progressChecker = makeProgressChecker(fakeInstance(INSTANCE_ID, RdsInstanceStatus.CREATING), create);
    whenDescribeInstance(fakeInstance(INSTANCE_ID, acceptableFollowupStatus));
    progressChecker.followupCheck(WAIT_NUM);
    assertFalse(progressChecker.isDone());
    verifyDescribeInstance();
  }

  @Test
  public void testFollowupCheckCreate_Creating()
  {
    testFollowupCheck_Acceptable(RdsInstanceStatus.CREATING, true);
  }

  @Test
  public void testFollowupCheckModify_Creating()
  {
    testFollowupCheck_Acceptable(RdsInstanceStatus.MODIFYING, false);
  }

  /**
   * Followup shows deleting = end with error.
   */
  private void testFollowupCheck_Deleting(boolean create)
  {
    RdsInstanceProgressChecker progressChecker = makeProgressChecker(fakeInstance(INSTANCE_ID, RdsInstanceStatus.CREATING), create);
    whenDescribeInstance(fakeInstance(INSTANCE_ID, RdsInstanceStatus.DELETING));
    progressChecker.followupCheck(WAIT_NUM);
    assertTrue(progressChecker.isDone());
    assertNull(progressChecker.getResult());
    verifyDescribeInstance();
  }

  @Test
  public void testFollowupCheckCreate_Deleting()
  {
    testFollowupCheck_Deleting(true);
  }

  @Test
  public void testFollowupCheckModify_Deleting()
  {
    testFollowupCheck_Deleting(false);
  }

  /**
   * Followup shows wrong snapshot id = throw.
   */
  private void testFollowupCheck_WrongId(RdsInstanceStatus acceptableFollowupStatus, boolean create)
  {
    RdsInstanceProgressChecker progressChecker = makeProgressChecker(fakeInstance(INSTANCE_ID, RdsInstanceStatus.CREATING), create);
    whenDescribeInstance(fakeInstance(ANOTHER_INSTANCE_ID, acceptableFollowupStatus));
    progressChecker.followupCheck(WAIT_NUM);
  }

  @Test(expected = IllegalStateException.class)
  public void testFollowupCheckCreate_WrongId()
  {
    testFollowupCheck_WrongId(RdsInstanceStatus.CREATING, true);
  }

  @Test(expected = IllegalStateException.class)
  public void testFollowupCheckModify_WrongId()
  {
    testFollowupCheck_WrongId(RdsInstanceStatus.MODIFYING, false);
  }

  /**
   * Followup shows available = done.
   */
  private void testFollowupCheck_Available(boolean create)
  {
    RdsInstanceProgressChecker progressChecker = makeProgressChecker(fakeInstance(INSTANCE_ID, RdsInstanceStatus.CREATING), create);
    DBInstance followupInstance = fakeInstance(INSTANCE_ID, RdsInstanceStatus.AVAILABLE);
    whenDescribeInstance(followupInstance);
    progressChecker.followupCheck(WAIT_NUM);
    assertTrue(progressChecker.isDone());
    assertEquals(followupInstance, progressChecker.getResult());
    verifyDescribeInstance();
  }

  @Test
  public void testFollowupCheckCreate_Available()
  {
    testFollowupCheck_Available(true);
  }

  @Test
  public void testFollowupCheckModify_Available()
  {
    testFollowupCheck_Available(false);
  }
}