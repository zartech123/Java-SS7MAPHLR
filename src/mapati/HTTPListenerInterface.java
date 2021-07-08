package mapati;

import javax.slee.SbbLocalObject;


/**
 * Defines the local interface for a Diameter Accounting client SBB.
 *
 * The state machine implemented by DiameterAccountingClientSbb is such that the
 * only valid sequence of method calls is
 * sendStartACR() , sendInterimACR()* , sendStopACR()
 * or sendEventACR() / sendSyncEventACR() before starting any session.
 *
 * When the caller receives a CouldNotSendACRException from one of the methods,
 * the session should be aborted -- no further method calls should be made on the SBB.
 *
 * The intention of this interface is to keep the DiameterAccountingClientSbb
 * free of the code which manages incoming events from the load generator
 * (e.g. from telnet connection), and to provide substitability of the 'driving'
 * SBB (e.g. to be able to drive using Example RA messages).
 */
public interface HTTPListenerInterface extends SbbLocalObject {

	
}