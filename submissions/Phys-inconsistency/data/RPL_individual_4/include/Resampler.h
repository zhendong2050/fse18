/*
 * Resampler.h
 *
 *  Created on: Dec 5, 2014
 *      Author: Filippo Martinoni
 *      Note: This class implement two different kind of resampling.
 */

#ifndef MAFILIPP_PARTICLE_FILTER_SRC_RESAMPLER_H_
#define MAFILIPP_PARTICLE_FILTER_SRC_RESAMPLER_H_

#include <ros/ros.h>

#include <math.h>
#include <random>
#include <iostream>
#include <chrono>

#include "map.h"
#include "Particle.h"

class Resampler {
public:
	Resampler(Particle * pc, int numPart, Map *map, double * cor);
	virtual ~Resampler();

	// This function resample the particle so that every particle outside the dimension of the wall or inside a wall is replace
	// by another particle in another location
	void resampleMap();

	// This function resample the particle according to the correlation array generated by the sensor update
	void resampleUniversal();

private:
	Particle * particleCloud;
	int numberOfParticle;
	Map * mapPtr;
	double * correlation;
};

#endif /* MAFILIPP_PARTICLE_FILTER_SRC_RESAMPLER_H_ */