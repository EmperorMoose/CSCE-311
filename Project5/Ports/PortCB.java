/**
A. Pierce Matthews
12/07/17
CSCE-311 Project 5
*/

package osp.Ports;

import java.util.*;
import osp.IFLModules.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.Memory.*;
import osp.Utilities.*;

/**
   The studends module for dealing with ports. The methods
   that have to be implemented are do_create(),
   do_destroy(), do_send(Message msg), do_receive().


   @OSPProject Ports
*/

public class PortCB extends IflPortCB
{
  //Buffer for IO
  private int outBuffer;
  private int inBuffer;
    /**
       Creates a new port. This constructor must have

	   super();

       as its first statement.

       @OSPProject Ports
    */
    public PortCB()
    {
        // your code goes here
        super();
    }

    /**
       This method is called once at the beginning of the
       simulation. Can be used to initialize static variables.

       @OSPProject Ports
    */
    public static void init()
    {
        // your code goes here
        System.out.println("Initializer in PortCB has been called");
    }

    /**
        Sets the properties of a new port, passed as an argument.
        Creates new message buffer, sets up the owner and adds the port to
        the task's port list. The owner is not allowed to have more
        than the maximum number of ports, MaxPortsPerTask.

        @OSPProject Ports
    */
    public static PortCB do_create()
    {
        // your code goes here
        //This block creates a port
        PortCB port = new PortCB();
        TaskCB task = null;
        try { task = MMU.getPTBR().getTask(); }
        catch(NullPointerException e)
        {
          System.out.println("Problem" + e);
        }
        int numberOfPorts = task.getPortCount();

        //If theres too many ports we exit
        if(numberOfPorts == MaxPortsPerTask)
        {
          System.out.println("Too many ports");
          return null;
        }

        //If the port cannot be added, exit
        if(task.addPort(port) == FAILURE)
        {
          System.out.println("Failure adding port");
          return null;
        }

        //Construct the new port
        port.setTask(task);
        port.setStatus(PortLive);
        port.inBuffer = 0;
        port.outBuffer = 0;

        return port;
    }

    /** Destroys the specified port, and unblocks all threads suspended
        on this port. Delete all messages. Removes the port from
        the owners port list.
        @OSPProject Ports
    */
    public void do_destroy()
    {
        // your code goes here
        this.setStatus(PortDestroyed);
        this.notifyThreads();
        this.getTask().removePort(this);
        this.setTask(null);
    }

    /**
       Sends the message to the specified port. If the message doesn't fit,
       keep suspending the current thread until the message fits, or the
       port is killed. If the message fits, add it to the buffer. If
       receiving threads are blocked on this port, resume them all.

       @param msg the message to send.

       @OSPProject Ports
    */
    public int do_send(Message msg)
    {
        // your code goes here
        //Is message valid? If not, fail
        if(msg == null || (PortBufferLength < msg.getLength()))
        {
          System.out.println("Invalid Message");
          return FAILURE;
        }

        //Initialize message event
        SystemEvent msgEvent = new SystemEvent("suspend message");
        TaskCB task = null;
        ThreadCB thread = null;

        //Try getting the current thread
        try{
          task = MMU.getPTBR().getTask();
          thread = task.getCurrentThread();
        } catch(NullPointerException e) {
          System.out.println("Error in sending message");
        }

        //Suspend the message, change the flag, make the buffer
        thread.suspend(msgEvent);
        boolean msgSuspended = true;
        int bufferRoom ;

        //While the message is suspended, we make checks
        while(msgSuspended == true)
        {
          if(this.inBuffer < this.outBuffer)
          {
            bufferRoom = this.outBuffer - this.inBuffer;
          }
          if(this.inBuffer == this.outBuffer)
          {
            //bufferRoom should be the length of the portbuffer
            if(this.isEmpty())
            {
              bufferRoom = PortBufferLength;
            }
            else
            {
              bufferRoom = 0;
            }
          }
          else
          {
            bufferRoom = PortBufferLength + this.outBuffer - this.inBuffer;
          }
          //If the message is small enough, resume
          //paddedBuffer is to fix an error where occasionally event was falsely resumed
          int paddedBuffer = bufferRoom - 10;
          if(msg.getLength() <= paddedBuffer)
          {
            msgSuspended = false;
          }
          else
          {
            thread.suspend(this);
          }
          //If this thread is to be killed, kill it and exit
          if(thread.getStatus() == ThreadKill)
          {
            System.out.println("Threads Dead Baby");
            this.removeThread(thread);
            return FAILURE;
          }
          //if the target port isnt live, exit
          if(this.getStatus() != PortLive)
          {
            msgEvent.notifyThreads();
            return FAILURE;
          }

        }
          //All checks are good, form and send message
      this.appendMessage(msg);
      this.notifyThreads();
      this.inBuffer = (this.inBuffer + msg.getLength()) % PortBufferLength;
      msgEvent.notifyThreads();
      System.out.println("The message was sent");
      return SUCCESS;

    }

    /** Receive a message from the port. Only the owner is allowed to do this.
        If there is no message in the buffer, keep suspending the current
	thread until there is a message, or the port is killed. If there
	is a message in the buffer, remove it from the buffer. If
	sending threads are blocked on this port, resume them all.
	Returning null means FAILURE.

        @OSPProject Ports
    */
    public Message do_receive()
    {
        // your code goes here
        //Setup stuff
        TaskCB task = null;
        ThreadCB thread = null;
        try
        {
            task = MMU.getPTBR().getTask();
            thread = task.getCurrentThread();
        }catch (NullPointerException e)
        {
            System.out.println("Error" + e);
        }

        //If the tasks dont match for some reason, exit
        if(this.getTask() != task)
        {
          return null;
        }

        //Make the even and suspend
        SystemEvent recieveEvent = new SystemEvent("receive_msg");
        thread.suspend(recieveEvent);
        boolean msgSuspended = true;

        while(msgSuspended)
        {
          //If the thread is empty, suspend
          if(this.isEmpty())
          {
            thread.suspend(this);
          }
          else
          {
            msgSuspended = false;
          }
          //If it needs to be killed, exit
          if(thread.getStatus() == ThreadKill)
          {
            this.removeThread(thread);
            recieveEvent.notifyThreads();
            return null;
          }
          //If the port isnt live, exit
          if(this.getStatus() != PortLive)
          {
            recieveEvent.notifyThreads();
            return null;
          }
        }

        //Checks succeed, recieve message
        Message msg = this.removeMessage();
        this.outBuffer = (this.outBuffer + msg.getLength()) % PortBufferLength;
        this.notifyThreads();
        recieveEvent.notifyThreads();
        System.out.println("Message: " + msg);
        return msg;
    }

    /** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.

	@OSPProject Ports
    */
    public static void atError()
    {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.

	@OSPProject Ports
    */
    public static void atWarning()
    {
        // your code goes here

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
