/*===========================================================================
  TranThermalPolicy.h
  DESCRIPTION
  apply the tran thermal policy api
===========================================================================*/
#ifndef __TRAN_THERMAL_POLICY__H_
#define __TRAN_THERMAL_POLICY__H_
/*
 * Callable function to send data to thermal-engine
 * Parameters:
 *   req_data - The request data value to send
 * Return value:
 *   0 on success, -1 on failure
 */
int tran_thermal_send_data(int req_data);
#endif