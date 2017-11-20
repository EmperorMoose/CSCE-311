package osp.Resources;

import java.util.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Memory.*;

/**
    A. Pierce Matthews
    11/20/17
    CSCE-311
    Project 4- Resources
    Class ResourceCB is the core of the resource management module.
    Students implement all the do_* methods.
    @OSPProject Resources
*/
public class ResourceCB extends IflResourceCB
{
  private static Hashtable<ThreadCB,RRB> rrbTable = new Hashtable<ThreadCB,RRB>();
  private static RRB emptyRRB = new RRB(null,null,0);
    /**
       Creates a new ResourceCB instance with the given number of
       available instances. This constructor must have super(qty)
       as its first statement.

       @OSPProject Resources
    */
    public ResourceCB(int qty)
    {
        super(qty);

    }

    /**
       This method is called once, at the beginning of the
       simulation. Can be used to initialize static variables.

       @OSPProject Resources
    */
    public static void init()
    {
        // your code goes here

    }

    /**
       Tries to acquire the given quantity of this resource.
       Uses deadlock avoidance or detection depending on the
       strategy in use, as determined by ResourceCB.getDeadlockMethod().

       @param quantity
       @return The RRB corresponding to the request.
       If the request is invalid (quantity+allocated>total) then return null.

       @OSPProject Resources
    */
    public RRB  do_acquire(int quantity)
    {
      //temp thread
      ThreadCB t = null;

      try
        {
          t = MMU.getPTBR().getTask().getCurrentThread();
        }

      catch (NullPointerException e){}

      //If Allocated is > total, null
      if ((quantity + (getAllocated(t))) > getTotal())
      {
        return null;
      }
      if (rrbTable.containsKey(t) == false)
      {
        rrbTable.put(t, emptyRRB);
      }
      RRB newRRB = new RRB(t, this, quantity);

      //If we have enough available, grant immediatethisResourcely
      if (quantity <= getAvailable())
      {
        newRRB.grant();
      }
      else
      {
        if (t.getStatus() != ThreadWaiting)
        {
          newRRB.setStatus(Suspended);
          t.suspend(newRRB);
        }
        if(!rrbTable.containsValue(newRRB))
        {
        rrbTable.put(t, newRRB);
        }
      }
      return newRRB;
    }

    /**
       Performs deadlock detection.
       @return A vector of ThreadCB objects found to be in a deadlock.

       @OSPProject Resources
    */
    public static Vector do_deadlockDetection()
    {
      //This is a vector of the deadlocked threads. If its ever empty, no more deadlock
    	Vector deadlockedThreads = BankersAlgorithm();
    	if (deadlockedThreads == null)
    	{
    		return null;
    	}
    	Fix_deadLock(deadlockedThreads);
    	return deadlockedThreads;

    }

    /**
       When a thread was killed, this is called to release all
       the resources owned by that thread.

       @param thread -- the thread in question

       @OSPProject Resources
    */
    public static void do_giveupResources(ThreadCB thread)
    {
    	for (int i = 0; i < ResourceTable.getSize(); i++)
    	{
        	ResourceCB thisResource = ResourceTable.getResourceCB(i);
        	if (thisResource.getAllocated(thread) != 0)
        	{
        	   thisResource.setAvailable(thisResource.getAvailable() + thisResource.getAllocated(thread));
             thisResource.setAllocated(thread, 0);
        	}
    	}
    	rrbTable.remove(thread);

    	//Are any RRB's grantable
    	RRB GrantRRB = CheckGrantable();
    	while (GrantRRB != null)
    	{
        //If not being killed
    		if (GrantRRB.getThread().getStatus() != ThreadKill)
    		{
          //If not this thread
    			if (GrantRRB.getThread() != thread)
    			{
            //Grant RRB
    				GrantRRB.grant();
    			}
    		}
    		rrbTable.put(GrantRRB.getThread(), emptyRRB);
    		GrantRRB = CheckGrantable();
    	}
    }

    /**
        Release a previously acquired resource.

	@param quantity

        @OSPProject Resources
    */
    public void do_release(int quantity)
    {
    	ThreadCB t = null;
    	try {t = MMU.getPTBR().getTask().getCurrentThread();}
    	catch (NullPointerException e){}
    	int thisAllocated = getAllocated(t);
    	if (quantity > thisAllocated)
    	{
    		quantity = thisAllocated;
    	}
    	setAllocated(t, thisAllocated - quantity);
    	setAvailable(getAvailable() + quantity);
      //Is grantable?
    	RRB GrantRRB = CheckGrantable();
    	while (GrantRRB != null)
    	{
    		if (GrantRRB.getThread().getStatus() != ThreadKill)
    		{
    			GrantRRB.setStatus(Granted);
    			GrantRRB.grant();
    		}
    		rrbTable.put(GrantRRB.getThread(), emptyRRB);
    		//Find another grantable RRB
        GrantRRB = CheckGrantable();
    	}
    }

    /** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.

	@OSPProject Resources
    */
    public static void atError()
    {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.

	@OSPProject Resources
    */
    public static void atWarning()
    {
        // your code goes here

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

    public static RRB CheckGrantable()
    {
      //Use a collection and an Iterator to move through the hash table
    	Collection rrbTableCollection = rrbTable.values();
    	Iterator rrbIterator = rrbTableCollection.iterator();
    	RRB Check = emptyRRB;
    	//Iterate while there are more elements in the collection
    	while(rrbIterator.hasNext())
    	{
    		Check = (RRB)rrbIterator.next();
    		ThreadCB TempThread = Check.getThread();
    		if(Check.getThread() == null)
    		{
    			continue;
    		}
        //If RRB is found
    		if (Check != null)
    		{
    			boolean Grant = false;
    			for (int i = 0; i < ResourceTable.getSize(); i++)
    			{
    				ResourceCB thisResource = ResourceTable.getResourceCB(i);
    				if (thisResource == Check.getResource())
    				{
              //If possible, grant the rrb
    					if (Check.getQuantity() <= Check.getResource().getAvailable())
      					{
       						Grant = true;
       					}
    				}
   				}
    			if (Grant == true)
    			{
    				return Check;
    			}
    		}
    	}
    	return null;
    }

    //The main algorithm
    public static Vector<ThreadCB> BankersAlgorithm()
    {
    	int[] resourceTableSize = new int[ResourceTable.getSize()];
    	for (int i = 0; i < resourceTableSize.length; i++)
    	{
    		resourceTableSize[i] = ResourceTable.getResourceCB(i).getAvailable();
    	}
    	Enumeration keys = rrbTable.keys();

      //This hastable is for the deadlock
    	Hashtable<ThreadCB, Boolean> deadlockTable = new Hashtable<ThreadCB, Boolean>();
    	while (keys.hasMoreElements())
    	{
    		ThreadCB CurrentThread = (ThreadCB)keys.nextElement();
    		deadlockTable.put(CurrentThread, false);
    	}

    	//2nd Loop
    	keys = rrbTable.keys();
    	while (keys.hasMoreElements())
    	{
    		ThreadCB CurrentThread = (ThreadCB)keys.nextElement();
    		for (int i = 0; i < ResourceTable.getSize(); i++)
    		{
    			ResourceCB thisResource = ResourceTable.getResourceCB(i);
    			if (thisResource.getAllocated(CurrentThread) != 0)
   				{
   					deadlockTable.put(CurrentThread, true);
   				}
    		}
    	}

    	boolean Repeat = true;
    	while (Repeat == true)
    	{
    		Repeat = false;

    		//3rd Loop
    		keys = rrbTable.keys();
    		while (keys.hasMoreElements())
    		{
    			ThreadCB CurrentThread = (ThreadCB)keys.nextElement();
    	    //If this thread is on the dealocked hash
    			if ((Boolean)deadlockTable.get(CurrentThread) == true)
    			{
    				RRB thisRRB = rrbTable.get(CurrentThread);
    				int thisRequest = thisRRB.getQuantity();
    				ResourceCB thisResource = thisRRB.getResource();
    				//Can this be granted?
    				if (thisRequest == 0 || thisRequest <= resourceTableSize[thisResource.getID()])
    				{
    					//4th Loop
    					for (int i = 0; i < ResourceTable.getSize(); i++)
    		    		{
    		    			ResourceCB ResourcefromTable = ResourceTable.getResourceCB(i);
    		    			resourceTableSize[i] += ResourcefromTable.getAllocated(CurrentThread);
    		    		}
    					deadlockTable.put(CurrentThread, false);
    					Repeat = true;
    				}
    			}
    		}
    	}

    	//Now we will enumerate the boolean hash,
    	Enumeration deadlockVector = deadlockTable.keys();
    	Vector<ThreadCB> deadlockedThreads = new Vector<ThreadCB>();

    	while (deadlockVector.hasMoreElements())
    	{
    		ThreadCB thisThread = (ThreadCB)deadlockVector.nextElement();
    		boolean isDeadLocked = deadlockTable.get(thisThread);
    		if (isDeadLocked == true)
    		{
    			deadlockedThreads.add(thisThread);
    		}
    	}
    	resourceTableSize = null;
    	if (deadlockedThreads.isEmpty())
    	{
    		System.out.println("No Deadlock");
    		return null;
    	}
    	if (!deadlockedThreads.isEmpty())
    	{
    		System.out.println("Threads in deadlock");
    	}
    	return deadlockedThreads;
    }

    public static Vector<ThreadCB> Fix_deadLock(Vector<ThreadCB> ThreadsList)
    {
    	Vector<ThreadCB> deadlockThreads = ThreadsList;
    	while (deadlockThreads != null)
    	{
    		ThreadCB thisThread = (ThreadCB)deadlockThreads.get(0);
    		deadlockThreads = BankersAlgorithm();
    		if (deadlockThreads == null)
    		{
    			break;
    		}
    		thisThread.kill();
    		deadlockThreads.remove(thisThread);
    	}

      //Look for more grantable RRB
    	RRB GrantRRB = CheckGrantable();
    	while (GrantRRB != null)
    	{
    		System.out.println(rrbTable.get(GrantRRB));
    		if (GrantRRB.getThread().getStatus() != ThreadKill)
    		{
    			GrantRRB.setStatus(Granted); 
    			GrantRRB.grant();
    		}
    		rrbTable.put(GrantRRB.getThread(), emptyRRB);
        GrantRRB = CheckGrantable();
    	}
    	return deadlockThreads;
    }

}

/*
      Feel free to add local classes to improve the readability of your code
*/
