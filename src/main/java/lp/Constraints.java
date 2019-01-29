package lp;

import gui.elements.Scenario;
import gurobi.*;
import manager.Parameters;
import manager.elements.Service;
import org.graphstream.graph.Node;
import org.graphstream.graph.Path;
import output.Auxiliary;

import java.util.List;

import static output.Definitions.*;

public class Constraints {

   private OptimizationModel model;
   private Variables vars;
   private Parameters pm;

   public Constraints(Parameters pm, OptimizationModel optimizationModel, Scenario scenario, GRBModel initialModel) throws GRBException {
      this.pm = pm;
      this.model = optimizationModel;
      this.vars = optimizationModel.getVariables();
      linkUtilization();
      if (scenario.getConstraints().get("DVC1") || scenario.getConstraints().get("DVC2") || scenario.getConstraints().get("DVC3"))
         serverUtilization(true);
      else
         serverUtilization(false);
      maxUtilization();
      serviceDelay(initialModel);
      if (scenario.getConstraints().get("VAI3")) countNumberOfUsedServers();
      if (scenario.getConstraints().get("RPC1")) onePathPerDemand();
      if (scenario.getConstraints().get("RPI1")) activatePathForService();
      if (scenario.getConstraints().get("VRC2")) pathsConstrainedByFunctions();
      if (scenario.getConstraints().get("VAC1")) functionPlacement();
      if (scenario.getConstraints().get("VAC2")) oneFunctionPerDemand();
      if (scenario.getConstraints().get("VAI1")) mappingFunctionsWithDemands();
      if (scenario.getConstraints().get("VAC3")) functionSequenceOrder();
      if (scenario.getConstraints().get("VRC1")) pathsConstrainedByFunctionsVRC1();
      if (scenario.getConstraints().get("RPC2")) numberOfActivePathsBoundByService();
      if (scenario.getConstraints().get("VRC3")) constraintVRC3();
      if (scenario.getConstraints().get("VAI2")) constraintVAI2();
      if (scenario.getConstraints().get("VSC1")) constraintVSC1();
      if (scenario.getConstraints().get("VSC2")) constraintVSC2();
      if (scenario.getConstraints().get("VSC3")) constraintVSC3();
      if (scenario.getConstraints().get("DIC1")) constraintDIC1();
      if (scenario.getConstraints().get("DVC1")) constraintDVC1();
      if (scenario.getConstraints().get("DVC2")) constraintDVC2();
      if (scenario.getConstraints().get("DVC3")) constraintDVC3();
      if (scenario.getConstraints().get("RPC3")) noParallelPaths();
      if (scenario.getConstraints().get("IPC1"))
         initialPlacementAsConstraints(initialModel);
   }

   private void linkUtilization() throws GRBException {
      for (int l = 0; l < pm.getLinks().size(); l++) {
         GRBLinExpr expr = new GRBLinExpr();
         for (int s = 0; s < pm.getServices().size(); s++)
            for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++) {
               if (!pm.getServices().get(s).getTrafficFlow().getPaths().get(p).contains(pm.getLinks().get(l)))
                  continue;
               for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++)
                  expr.addTerm((double) pm.getServices().get(s).getTrafficFlow().getDemands().get(d)
                          / (int) pm.getLinks().get(l).getAttribute("capacity"), vars.rSPD[s][p][d]);
            }
         for (int s = 0; s < pm.getServices().size(); s++)
            for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
               for (int p = 0; p < pm.getPaths().size(); p++) {
                  if (!pm.getPaths().get(p).contains(pm.getLinks().get(l)))
                     continue;
                  double traffic = 0;
                  for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++)
                     traffic += pm.getServices().get(s).getTrafficFlow().getDemands().get(d)
                             * (double) pm.getServices().get(s).getFunctions().get(v).getAttribute("load");
                  expr.addTerm(traffic / (int) pm.getLinks().get(l).getAttribute("capacity"), vars.sSVP[s][v][p]);
               }
         model.getGrbModel().addConstr(expr, GRB.EQUAL, vars.uL[l], "linkUtilization");
         linearCostFunctions(expr, vars.kL[l]);
      }
   }

   private void serverUtilization(boolean isOverheadVariable) throws GRBException {
      for (int x = 0; x < pm.getServers().size(); x++) {
         GRBLinExpr serverUtilizationExpr = new GRBLinExpr();
         for (int s = 0; s < pm.getServices().size(); s++)
            for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
               for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++) {
                  serverUtilizationExpr.addTerm((pm.getServices().get(s).getTrafficFlow().getDemands().get(d)
                                  * (double) pm.getServices().get(s).getFunctions().get(v).getAttribute("load"))
                                  / pm.getServers().get(x).getCapacity()
                          , vars.pXSVD[x][s][v][d]);
               }
               if (isOverheadVariable) {
                  GRBLinExpr variableOverheadExpr = new GRBLinExpr();
                  variableOverheadExpr.addTerm((double) pm.getServices().get(s).getFunctions().get(v).getAttribute("overhead") / pm.getServers().get(x).getCapacity()
                                , vars.nXSV[x][s][v]);
                        serverUtilizationExpr.add(variableOverheadExpr);

               } else {
                  GRBLinExpr fixOverheadExpr = new GRBLinExpr();
                  fixOverheadExpr.addTerm((double) pm.getServices().get(s).getFunctions().get(v).getAttribute("overhead")
                                  * (int) pm.getServices().get(s).getFunctions().get(v).getAttribute("maxInstances")
                                  / pm.getServers().get(x).getCapacity()
                          , vars.pXSV[x][s][v]);
                  serverUtilizationExpr.add(fixOverheadExpr);
               }
            }
         model.getGrbModel().addConstr(serverUtilizationExpr, GRB.EQUAL, vars.uX[x], "serverUtilization");
         linearCostFunctions(serverUtilizationExpr, vars.kX[x]);
      }
   }

   private void linearCostFunctions(GRBLinExpr expr, GRBVar grbVar) throws GRBException {
      for (int l = 0; l < Auxiliary.costFunctions.getValues().size(); l++) {
         GRBLinExpr expr2 = new GRBLinExpr();
         expr2.multAdd(Auxiliary.costFunctions.getValues().get(l)[0], expr);
         expr2.addConstant(Auxiliary.costFunctions.getValues().get(l)[1]);
         model.getGrbModel().addConstr(expr2, GRB.LESS_EQUAL, grbVar, "costFunctions");
      }
   }

   private void maxUtilization() throws GRBException {
      for (int x = 0; x < pm.getServers().size(); x++)
         model.getGrbModel().addConstr(vars.uX[x], GRB.LESS_EQUAL, vars.uMax, "");
      for (int l = 0; l < pm.getLinks().size(); l++)
         model.getGrbModel().addConstr(vars.uL[l], GRB.LESS_EQUAL, vars.uMax, "");
   }

   private void serviceDelay(GRBModel initialModel) throws GRBException {
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++)
            for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++) {
               GRBLinExpr serviceDelayExpr = new GRBLinExpr();
               serviceDelayExpr.add(linkDelayExpr(s, p));
               for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
                  serviceDelayExpr.add(processingDelayExpr(s, p, v, d));
               model.getGrbModel().addConstr(serviceDelayExpr, GRB.LESS_EQUAL
                       , (int) pm.getServices().get(s).getAttribute("max_delay"), "");
//               model.getGrbModel().addConstr(serviceDelayExpr, GRB.LESS_EQUAL, vars.dSPD[s][p][d], "");
            }
   }

   private GRBLinExpr linkDelayExpr(int s, int p) {
      Path path = pm.getServices().get(s).getTrafficFlow().getPaths().get(p);
      GRBLinExpr linkDelayExpr = new GRBLinExpr();
      double pathDelay = 0.0;
      for (int l = 0; l < path.getEdgePath().size(); l++)
         pathDelay += (double) path.getEdgePath().get(l).getAttribute(LINK_DELAY);
      linkDelayExpr.addTerm(pathDelay, vars.rSP[s][p]);
      return linkDelayExpr;
   }

   private GRBLinExpr processingDelayExpr(int s, int p, int v, int d) {
      Service service = pm.getServices().get(s);
      Path path = service.getTrafficFlow().getPaths().get(p);
      GRBLinExpr processDelayExpr = new GRBLinExpr();
      for (int n = 0; n < path.getNodePath().size(); n++)
         for (int x = 0; x < pm.getServers().size(); x++)
            if (pm.getServers().get(x).getParent().equals(path.getNodePath().get(n)))
               processDelayExpr.addTerm((int) service.getFunctions().get(v).getAttribute(PROCESS_DELAY)
                       , vars.pXSVD[x][s][v][d]);
      return processDelayExpr;
   }

   private void countNumberOfUsedServers() throws GRBException {            //VAI 3
      for (int x = 0; x < pm.getServers().size(); x++) {
         GRBLinExpr expr = new GRBLinExpr();
         GRBLinExpr expr2 = new GRBLinExpr();
         for (int s = 0; s < pm.getServices().size(); s++)
            for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
               expr.addTerm(1.0 / pm.getTotalNumFunctions(), vars.pXSV[x][s][v]);
               expr2.addTerm(1.0, vars.pXSV[x][s][v]);
            }
         model.getGrbModel().addConstr(vars.pX[x], GRB.GREATER_EQUAL, expr, "countNumberOfUsedServers");
         model.getGrbModel().addConstr(vars.pX[x], GRB.LESS_EQUAL, expr2, "countNumberOfUsedServers");
      }
   }

   private void onePathPerDemand() throws GRBException {            //RPC 1
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++) {
            GRBLinExpr expr = new GRBLinExpr();
            for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++)
               expr.addTerm(1.0, vars.rSPD[s][p][d]);
            model.getGrbModel().addConstr(expr, GRB.EQUAL, 1.0, "onePathPerDemand");
         }
   }

   private void activatePathForService() throws GRBException {            //RPI 1
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++) {
            GRBLinExpr expr = new GRBLinExpr();
            GRBLinExpr expr2 = new GRBLinExpr();
            for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++) {
               expr.addTerm(1.0, vars.rSPD[s][p][d]);
               expr2.addTerm(1.0 / (pm.getServices().get(s).getTrafficFlow().getDemands().size() * 10), vars.rSPD[s][p][d]);
            }
            model.getGrbModel().addConstr(expr, GRB.GREATER_EQUAL, vars.rSP[s][p], "activatePathForService");
            model.getGrbModel().addConstr(expr2, GRB.LESS_EQUAL, vars.rSP[s][p], "activatePathForService");
         }
   }

    /*private void activatePathForService() throws GRBException {
        for (int s = 0; s < pm.getServices().size(); s++)
            for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getAdmissiblePaths().size(); p++)
                for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getTrafficDemands().size(); d++)
                    model.getGrbModel().addConstr(variables.rSPD[s][p][d], GRB.LESS_EQUAL, variables.rSP[s][p], "activatePathForService");

        for (int s = 0; s < pm.getServices().size(); s++)
            for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getAdmissiblePaths().size(); p++) {
                GRBLinExpr expr = new GRBLinExpr();
                for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getTrafficDemands().size(); d++)
                    expr.addTerm(1.0, variables.rSPD[s][p][d]);
                model.getGrbModel().addConstr(expr, GRB.GREATER_EQUAL, variables.rSP[s][p], "activatePathForService");
            }
    }*/

   private void pathsConstrainedByFunctions() throws GRBException {            //VRC 2
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
            GRBLinExpr expr = new GRBLinExpr();
            for (int x = 0; x < pm.getServers().size(); x++)
               expr.addTerm(1.0, vars.pXSV[x][s][v]);
            if ((boolean) pm.getServices().get(s).getFunctions().get(v).getAttribute("replicable")) {
               GRBLinExpr expr2 = new GRBLinExpr();
               for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++)
                  expr2.addTerm(1.0, vars.rSP[s][p]);
               model.getGrbModel().addConstr(expr, GRB.EQUAL, expr2, "pathsConstrainedByFunctions");
            } else
               model.getGrbModel().addConstr(expr, GRB.EQUAL, 1.0, "pathsConstrainedByFunctions");
         }
   }

   private void functionPlacement() throws GRBException {              //VAC 1
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++)
            for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++)
               for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
                  GRBLinExpr expr = new GRBLinExpr();
                  for (int n = 0; n < pm.getServices().get(s).getTrafficFlow().getPaths().get(p).getNodePath().size(); n++)
                     for (int x = 0; x < pm.getServers().size(); x++)
                        if (pm.getServers().get(x).getParent().equals(pm.getServices().get(s).getTrafficFlow().getPaths().get(p).getNodePath().get(n)))
                           expr.addTerm(1.0, vars.pXSVD[x][s][v][d]);
                  model.getGrbModel().addConstr(vars.rSPD[s][p][d], GRB.LESS_EQUAL, expr, "functionPlacement");
               }
   }

   private void oneFunctionPerDemand() throws GRBException {            //VAC 2
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
            for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++) {
               GRBLinExpr expr = new GRBLinExpr();
               for (int x = 0; x < pm.getServers().size(); x++)
                  expr.addTerm(1.0, vars.pXSVD[x][s][v][d]);
               model.getGrbModel().addConstr(expr, GRB.EQUAL, 1.0, "oneFunctionPerDemand");
            }
   }

   private void mappingFunctionsWithDemands() throws GRBException {            //VAI 1

      for (int s = 0; s < pm.getServices().size(); s++)
         for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
            for (int x = 0; x < pm.getServers().size(); x++) {
               GRBLinExpr expr = new GRBLinExpr();
               GRBLinExpr expr2 = new GRBLinExpr();
               for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++) {
                  expr.addTerm(1.0, vars.pXSVD[x][s][v][d]);
                  expr2.addTerm(1.0 / (pm.getServices().get(s).getTrafficFlow().getDemands().size() * 10), vars.pXSVD[x][s][v][d]);
               }
               model.getGrbModel().addConstr(expr, GRB.GREATER_EQUAL, vars.pXSV[x][s][v], "mappingFunctionsWithDemands");
               model.getGrbModel().addConstr(expr2, GRB.LESS_EQUAL, vars.pXSV[x][s][v], "mappingFunctionsWithDemands");
            }


   }

    /*private void mappingFunctionsWithDemands() throws GRBException {

        for (int s = 0; s < pm.getServices().size(); s++)
            for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
                for (int x = 0; x < pm.getServers().size(); x++)
                    for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getTrafficDemands().size(); d++)
                        model.getGrbModel().addConstr(variables.pXSVD[x][s][v][d], GRB.LESS_EQUAL, variables.pXSV[x][s][v], "mappingFunctionsWithDemands");

        for (int s = 0; s < pm.getServices().size(); s++)
            for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
                for (int x = 0; x < pm.getServers().size(); x++) {
                    GRBLinExpr expr = new GRBLinExpr();
                    for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getTrafficDemands().size(); d++)
                        expr.addTerm(1.0, variables.pXSVD[x][s][v][d]);
                    model.getGrbModel().addConstr(expr, GRB.GREATER_EQUAL, variables.pXSV[x][s][v], "mappingFunctionsWithDemands");
                }
    }*/

   private void functionSequenceOrder() throws GRBException {            //VAC 3
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++) {
            for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++)
               for (int v = 1; v < pm.getServices().get(s).getFunctions().size(); v++) {
                  for (int n = 0; n < pm.getServices().get(s).getTrafficFlow().getPaths().get(p).getNodePath().size(); n++) {
                     GRBLinExpr expr = new GRBLinExpr();
                     GRBLinExpr expr2 = new GRBLinExpr();
                     Node nodeN = pm.getServices().get(s).getTrafficFlow().getPaths().get(p).getNodePath().get(n);
                     for (int m = 0; m <= n; m++) {
                        Node nodeM = pm.getServices().get(s).getTrafficFlow().getPaths().get(p).getNodePath().get(m);
                        for (int x = 0; x < pm.getServers().size(); x++)
                           if (pm.getServers().get(x).getParent().equals(nodeM))
                              expr.addTerm(1.0, vars.pXSVD[x][s][v - 1][d]);
                     }
                     for (int x = 0; x < pm.getServers().size(); x++)
                        if (pm.getServers().get(x).getParent().equals(nodeN))
                           expr.addTerm(-1.0, vars.pXSVD[x][s][v][d]);

                     expr2.addConstant(-1);
                     expr2.addTerm(1.0, vars.rSPD[s][p][d]);
                     model.getGrbModel().addConstr(expr, GRB.GREATER_EQUAL, expr2, "functionSequenceOrder");
                  }
               }
         }
   }

   //additional constraints
   private void pathsConstrainedByFunctionsVRC1() throws GRBException {            //VRC 1
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
            GRBLinExpr expr = new GRBLinExpr();
            for (int x = 0; x < pm.getServers().size(); x++)
               expr.addTerm(1.0, vars.pXSV[x][s][v]);
            if ((boolean) pm.getServices().get(s).getFunctions().get(v).getAttribute("replicable")) {
               GRBLinExpr expr2 = new GRBLinExpr();
               for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++)
                  expr2.addTerm(1.0, vars.rSP[s][p]);
               model.getGrbModel().addConstr(expr, GRB.LESS_EQUAL, expr2, "pathsConstrainedByFunctions");
            } else
               model.getGrbModel().addConstr(expr, GRB.LESS_EQUAL, 1.0, "pathsConstrainedByFunctions");
         }
   }

   private void numberOfActivePathsBoundByService() throws GRBException {            //RPC 2
      for (int s = 0; s < pm.getServices().size(); s++) {
         int rmin = (int) pm.getServices().get(s).getAttribute("minPaths");
         int rmax = (int) pm.getServices().get(s).getAttribute("maxPaths");
         GRBLinExpr expr = new GRBLinExpr();
         for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++) {
            expr.addTerm(1.0, vars.rSP[s][p]);
         }
         model.getGrbModel().addConstr(expr, GRB.LESS_EQUAL, rmax, "numberOfActivePathsBoundByService");
         model.getGrbModel().addConstr(expr, GRB.GREATER_EQUAL, rmin, "numberOfActivePathsBoundByService");
      }
   }

   private void constraintVRC3() throws GRBException {             //VRC 3
      for (int s = 0; s < pm.getServices().size(); s++) {
         for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
            GRBLinExpr expr = new GRBLinExpr();
            for (int x = 0; x < pm.getServers().size(); x++)
               expr.addTerm(1.0, vars.pXSV[x][s][v]);
            boolean replicable = (boolean) pm.getServices().get(s).getFunctions().get(v).getAttribute("replicable");
            if (replicable) {
               int minRep = (int) pm.getServices().get(s).getAttribute("minReplica") + 1;
               int maxRep = (int) pm.getServices().get(s).getAttribute("maxReplica") + 1;
               model.getGrbModel().addConstr(expr, GRB.GREATER_EQUAL, minRep, "constraintVRC3");
               model.getGrbModel().addConstr(expr, GRB.LESS_EQUAL, maxRep, "constraintVRC3");
            } else {
               model.getGrbModel().addConstr(expr, GRB.EQUAL, 1, "constraintVRC3");
            }
         }
      }
   }

   private void constraintVAI2() throws GRBException {
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int x = 0; x < pm.getServers().size(); x++) {
            GRBLinExpr expr = new GRBLinExpr();
            GRBLinExpr expr2 = new GRBLinExpr();
            for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
               expr.addTerm(1.0, vars.pXSV[x][s][v]);
               expr2.addTerm(1.0 / pm.getServices().get(s).getFunctions().size(), vars.pXSV[x][s][v]);
            }
            model.getGrbModel().addConstr(expr, GRB.GREATER_EQUAL, vars.pXS[x][s], "constraintVAI2");
            model.getGrbModel().addConstr(expr2, GRB.LESS_EQUAL, vars.pXS[x][s], "constraintVAI2");
         }
   }

   private void constraintVSC1() throws GRBException {
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int x = 0; x < pm.getServers().size(); x++) {
            GRBLinExpr expr = new GRBLinExpr();
            for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
               expr.addTerm(1.0, vars.pXSV[x][s][v]);
            int maxVNF = (int) pm.getServices().get(s).getAttribute("maxVNFserver");
            model.getGrbModel().addConstr(expr, GRB.LESS_EQUAL, maxVNF, "constraintVSC1");
         }
   }

   private void constraintVSC2() throws GRBException {
      for (int x = 0; x < pm.getServers().size(); x++) {
         GRBLinExpr expr = new GRBLinExpr();
         for (int s = 0; s < pm.getServices().size(); s++)
            expr.addTerm(1.0, vars.pXS[x][s]);
         int maxSFC = pm.getServers().get(x).getParent().getAttribute("MaxSFC");
         model.getGrbModel().addConstr(expr, GRB.LESS_EQUAL, maxSFC, "constraintVSC2");
      }
   }

   private void constraintVSC3() throws GRBException {
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
            for (int x = 0; x < pm.getServers().size(); x++) {
               GRBLinExpr expr = new GRBLinExpr();
               for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++)
                  expr.addTerm(1.0, vars.pXSVD[x][s][v][d]);
               int maxSubflow = (int) pm.getServices().get(s).getFunctions().get(v).getAttribute("maxsubflows");
               model.getGrbModel().addConstr(expr, GRB.LESS_EQUAL, maxSubflow, "constraintVSC3");
            }
   }

   private void constraintDIC1() throws GRBException {
      for (int x = 0; x < pm.getServers().size(); x++)
         for (int s = 0; s < pm.getServices().size(); s++)
            for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
               List<Integer> sharedNF = (List<Integer>) pm.getServices().get(s).getAttribute("sharedNF");
 //              for (int i = 0; i < sharedNF.size(); i++)
                  if (sharedNF.get(v) == 0) {
                     double load = (double) pm.getServices().get(s).getFunctions().get(v).getAttribute("load");
                     GRBLinExpr expr = new GRBLinExpr();
                     for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++)
                        expr.addTerm(load * pm.getServices().get(s).getTrafficFlow().getDemands().get(d), vars.pXSVD[x][s][v][d]);
                     int maxLoad = (int) pm.getServices().get(s).getFunctions().get(v).getAttribute("maxLoad");
                     int maxInt = (int) pm.getServices().get(s).getFunctions().get(v).getAttribute("maxInstances");
                     model.getGrbModel().addConstr(expr, GRB.LESS_EQUAL, maxLoad * maxInt, "constraintDIC1");
                  }
            }
   }

   private void constraintDVC1() throws GRBException {
      for (int x = 0; x < pm.getServers().size(); x++)
         for (int s = 0; s < pm.getServices().size(); s++)
            for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
               List<Integer> sharedNF = (List<Integer>) pm.getServices().get(s).getAttribute("sharedNF");
  //             for (int i = 0; i < sharedNF.size(); i++)
                  if (sharedNF.get(v) == 0) {
                     double load = (double) pm.getServices().get(s).getFunctions().get(v).getAttribute("load");
                     GRBLinExpr expr = new GRBLinExpr();
                     GRBLinExpr expr2 = new GRBLinExpr();
                     int maxLoad = (int) pm.getServices().get(s).getFunctions().get(v).getAttribute("maxLoad");
                     for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++) {
                        expr.addTerm(load * pm.getServices().get(s).getTrafficFlow().getDemands().get(d), vars.pXSVD[x][s][v][d]);
                     }
                     expr2.addTerm(maxLoad, vars.nXSV[x][s][v]);
                     model.getGrbModel().addConstr(expr, GRB.LESS_EQUAL, expr2, "constraintDVC1");
                  }
            }
   }

   private void constraintDVC2() throws GRBException {
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
            for (int x = 0; x < pm.getServers().size(); x++) {
               GRBLinExpr expr = new GRBLinExpr();
               GRBLinExpr expr2 = new GRBLinExpr();
               GRBLinExpr expr3 = new GRBLinExpr();
               int maxInst = (int) pm.getServices().get(s).getFunctions().get(v).getAttribute("maxInstances");
               expr.addTerm(1.0, vars.pXSV[x][s][v]);
               expr2.addTerm(1.0, vars.nXSV[x][s][v]);
               expr3.addTerm(maxInst, vars.pXSV[x][s][v]);
               //String strExpr = Integer.toString(expr);
               //model.getGrbModel().addConstr(Integer.parseInt(strExpr, 2), GRB.LESS_EQUAL, expr2, "constraintDVC2");
               //model.getGrbModel().addConstr(expr2, GRB.LESS_EQUAL, Integer.parseInt(strExpr, 2) * maxInst, "constraintDVC");
               model.getGrbModel().addConstr(expr, GRB.LESS_EQUAL, expr2, "constraintDVC2");
               model.getGrbModel().addConstr(expr2, GRB.LESS_EQUAL, expr3, "constraintDVC");
            }
   }

   private void constraintDVC3() throws GRBException {
      for (int x = 0; x < pm.getServers().size(); x++)
         for (int s = 0; s < pm.getServices().size(); s++)
            for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
               List<Integer> sharedNF = (List<Integer>) pm.getServices().get(s).getAttribute("sharedNF");
               for (int i = 0; i < sharedNF.size(); i++)
                  if (sharedNF.get(i) == 0) {
                     GRBLinExpr expr = new GRBLinExpr();
                     GRBLinExpr expr2 = new GRBLinExpr();
                     double load = (double) pm.getServices().get(s).getFunctions().get(v).getAttribute("load");
                     int maxLoad = (int) pm.getServices().get(s).getFunctions().get(v).getAttribute("maxLoad");
                     for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++)
                        expr.addTerm(load * pm.getServices().get(s).getTrafficFlow().getDemands().get(d), vars.pXSVD[x][s][v][d]);
                     expr2.addTerm(maxLoad, vars.nXSV[x][s][v]);
                     model.getGrbModel().addConstr(expr, GRB.LESS_EQUAL, expr2, "constraintDVC3");
                     expr.addConstant(maxLoad);
                     model.getGrbModel().addConstr(expr, GRB.GREATER_EQUAL, expr2, "constraintDVC3");
                  }
            }
   }

   //check parameters used
   private void test() {
      for (int s = 0; s < pm.getServices().size(); s++) {
         for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
            int minPaths = (int) pm.getServices().get(s).getAttribute("minPaths");
            boolean replicable = (boolean) pm.getServices().get(s).getFunctions().get(v).getAttribute("replicable");
            double load = (double) pm.getServices().get(s).getFunctions().get(v).getAttribute("load");
            int overhead = (int) pm.getServices().get(s).getFunctions().get(v).getAttribute("overhead");
            List<Integer> sharedNF = (List<Integer>) pm.getServices().get(s).getAttribute("sharedNF");
            for (int i = 0; i < sharedNF.size(); i++) {
               System.out.println(sharedNF.get(i));
            }
            System.out.println(load);
            System.out.println(replicable);
            System.out.println(minPaths);
            System.out.println(overhead);
         }
      }
      for (int x = 0; x < pm.getServers().size(); x++) {
         int maxSFC = (int) pm.getServers().get(x).getParent().getAttribute("MaxSFC");
         System.out.println(maxSFC);
      }

   }

   //Use Case Constraints
   private void noParallelPaths() throws GRBException {            //RPC 3
      for (int s = 0; s < pm.getServices().size(); s++) {
         GRBLinExpr expr = new GRBLinExpr();
         for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++)
            expr.addTerm(1.0, vars.rSP[s][p]);
         model.getGrbModel().addConstr(expr, GRB.EQUAL, 1, "noParallelPaths");
      }
   }

   private void initialPlacementAsConstraints(GRBModel initialModel) throws GRBException {            //IPC 1
      if (initialModel != null) {
         for (int x = 0; x < pm.getServers().size(); x++)
            for (int s = 0; s < pm.getServices().size(); s++)
               for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
                  if (initialModel.getVarByName(pXSV + "[" + x + "][" + s + "][" + v + "]").get(GRB.DoubleAttr.X) == 1.0)
                     model.getGrbModel().addConstr(vars.pXSV[x][s][v], GRB.EQUAL, 1, "initialPlacementAsConstraints");
      }
   }

   private void constraintReplications() throws GRBException {
      for (int s = 0; s < pm.getServices().size(); s++) {
         GRBLinExpr expr = new GRBLinExpr();
         for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++)
            expr.addTerm(1.0, vars.rSP[s][p]);
         int minPaths = (int) pm.getServices().get(s).getAttribute("minPaths");
         int maxPaths = (int) pm.getServices().get(s).getAttribute("maxPaths");
         model.getGrbModel().addConstr(expr, GRB.GREATER_EQUAL, minPaths, "");
         model.getGrbModel().addConstr(expr, GRB.LESS_EQUAL, maxPaths, "");
      }
   }
}
