#ifndef _UTILITY_THERMAL_UX_H
#define _UTILITY_THERMAL_UX_H

extern int setCPUFreqThermalUxCluster0(int value, void *scn);
extern int setCPUFreqThermalUxCluster1(int value, void *scn);
extern int setCPUFreqThermalUxCluster2(int value, void *scn);

extern void thermalUxInit(bool is_first);
#endif

