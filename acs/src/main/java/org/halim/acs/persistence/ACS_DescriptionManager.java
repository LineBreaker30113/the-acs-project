package org.halim.acs.persistence;

import org.halim.acs.ACS_Actor;


public interface ACS_DescriptionManager extends ACS_Actor {

String getCoreDescription();
void setCoreDescription(String coreDescription);

String getArityDescription(int arityIndex);
void setArityDescription(int arityIndex, String arityDescription);

String getArityCatalogRowDescription(int arityIndex, int rowIndex);
void setArityCatalogRowDescription(int arityIndex, int rowIndex, String arityDescription);

String getStructureDescription(int structureIndex);
void setStructureDescription(int structureIndex, String structureDescription);

String getArityClassDescription(String className);
void setArityClassDescription(String className, String arityDescription);

String getArityCatalogRowClassDescription(String className);
void setArityCatalogRowClassDescription(String className, String arityDescription);

String getStructureClassDescription(String className);
void setStructureClassDescription(String className, String arityDescription);


}
