package lp.constraints;

import gui.elements.Scenario;
import gurobi.*;
import lp.OptimizationModel;
import lp.Variables;
import manager.Parameters;
import manager.elements.Function;
import manager.elements.Service;
import org.graphstream.graph.Path;

import static output.Definitions.*;

public class AdditionalConstraints {

   private OptimizationModel model;
   private Variables vars;
   private Parameters pm;

   public AdditionalConstraints(Parameters pm, OptimizationModel model, Scenario scenario, GRBModel initialModel, GRBLinExpr[] linkLoadExpr) {
      try {
         this.pm = pm;
         this.model = model;
         this.vars = model.getVariables();
         if (scenario.getConstraints().get(ST)) ST(linkLoadExpr);
         if (scenario.getConstraints().get(SD)) SD(initialModel);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   // synchronization traffic
   private void ST(GRBLinExpr[] linkLoadExpr) throws GRBException {
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
            for (int x = 0; x < pm.getServers().size(); x++)
               for (int y = 0; y < pm.getServers().size(); y++) {
                  if (pm.getServers().get(x).getParent().equals(pm.getServers().get(y).getParent())) continue;
                  model.getGrbModel().addConstr(vars.gSVXY[s][v][x][y], GRB.LESS_EQUAL, vars.fXSV[x][s][v], ST);
                  model.getGrbModel().addConstr(vars.gSVXY[s][v][x][y], GRB.LESS_EQUAL, vars.fXSV[y][s][v], ST);
                  GRBLinExpr expr = new GRBLinExpr();
                  expr.addTerm(1.0, vars.fXSV[x][s][v]);
                  expr.addTerm(1.0, vars.fXSV[y][s][v]);
                  expr.addConstant(-1.0);
                  model.getGrbModel().addConstr(vars.gSVXY[s][v][x][y], GRB.GREATER_EQUAL, expr, ST);
                  expr = new GRBLinExpr();
                  for (int p = 0; p < pm.getPaths().size(); p++) {
                     Path pa = pm.getPaths().get(p);
                     if (pa.getNodePath().get(0).equals(pm.getServers().get(x).getParent())
                             & pa.getNodePath().get(pa.getNodePath().size() - 1)
                             .equals(pm.getServers().get(y).getParent()))
                        expr.addTerm(1.0, vars.hSVP[s][v][p]);
                  }
                  model.getGrbModel().addConstr(vars.gSVXY[s][v][x][y], GRB.LESS_EQUAL, expr, ST);
                  model.getGrbModel().addConstr(expr, GRB.LESS_EQUAL, 1.0, ST);
               }
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
            for (int n = 0; n < pm.getNodes().size(); n++)
               for (int m = 0; m < pm.getNodes().size(); m++) {
                  if (n == m) continue;
                  GRBLinExpr expr = new GRBLinExpr();
                  for (int p = 0; p < pm.getPaths().size(); p++) {
                     Path path = pm.getPaths().get(p);
                     if (path.getNodePath().get(0).equals(pm.getNodes().get(n))
                             & path.getNodePath().get(path.getNodePath().size() - 1).equals(pm.getNodes().get(m)))
                        expr.addTerm(1.0, vars.hSVP[s][v][p]);
                  }
                  GRBLinExpr expr2 = new GRBLinExpr();
                  for (int x = 0; x < pm.getServers().size(); x++)
                     for (int y = 0; y < pm.getServers().size(); y++)
                        if (pm.getServers().get(x).getParent().equals(pm.getNodes().get(n))
                                && pm.getServers().get(y).getParent().equals(pm.getNodes().get(m)))
                           expr2.addTerm(1.0, vars.gSVXY[s][v][x][y]);
                  model.getGrbModel().addConstr(expr, GRB.LESS_EQUAL, expr2, ST);
               }

      for (int l = 0; l < pm.getLinks().size(); l++) {
         GRBLinExpr expr = new GRBLinExpr();
         for (int p = 0; p < pm.getPaths().size(); p++) {
            if (!pm.getPaths().get(p).contains(pm.getLinks().get(l)))
               continue;
            for (int s = 0; s < pm.getServices().size(); s++) {
               double traffic = 0;
               for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++)
                  traffic += pm.getServices().get(s).getTrafficFlow().getDemands().get(d);
               for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
                  double trafficScaled = traffic * (double) pm.getServices().get(s).getFunctions().get(v).getAttribute(FUNCTION_SYNC_LOAD_RATIO);
                  expr.addTerm(trafficScaled, vars.hSVP[s][v][p]);
               }
            }
         }
         linkLoadExpr[l].add(expr);
      }
   }

   // service delay
   private void SD(GRBModel initialModel) throws GRBException {
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++)
            for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++) {
               Service service = pm.getServices().get(s);
               Path path = service.getTrafficFlow().getPaths().get(p);
               GRBLinExpr processDelayGlobalExpr = new GRBLinExpr();
               for (int n = 0; n < path.getNodePath().size(); n++)
                  for (int x = 0; x < pm.getServers().size(); x++)
                     if (pm.getServers().get(x).getParent().equals(path.getNodePath().get(n)))
                        for (int v = 0; v < service.getFunctions().size(); v++) {
                           Function function = service.getFunctions().get(v);
                           GRBLinExpr processDelayExpr = processingDelayExpr(s, v, x, (int) function.getAttribute(FUNCTION_PROCESS_DELAY));
                           model.getGrbModel().addConstr(processDelayExpr, GRB.LESS_EQUAL
                                   , (int) service.getFunctions().get(v).getAttribute(FUNCTION_MAX_DELAY), FUNCTION_PROCESS_DELAY);
                           GRBLinExpr auxExpr = new GRBLinExpr();
                           auxExpr.addTerm((int) function.getAttribute(FUNCTION_PROCESS_DELAY), vars.fXSVD[x][s][v][d]);
                           model.getGrbModel().addConstr(vars.ySVXD[s][v][x][d], GRB.LESS_EQUAL, auxExpr, FUNCTION_PROCESS_DELAY);
                           model.getGrbModel().addConstr(vars.ySVXD[s][v][x][d], GRB.LESS_EQUAL, processDelayExpr, FUNCTION_PROCESS_DELAY);
                           auxExpr.addConstant(-(int) function.getAttribute(FUNCTION_PROCESS_DELAY));
                           auxExpr.add(processDelayExpr);
                           model.getGrbModel().addConstr(vars.ySVXD[s][v][x][d], GRB.GREATER_EQUAL, auxExpr, FUNCTION_PROCESS_DELAY);
                           processDelayGlobalExpr.addTerm(1.0, vars.ySVXD[s][v][x][d]);
                        }
               GRBLinExpr pathExpr = new GRBLinExpr();
               pathExpr.addTerm((int) service.getAttribute(SERVICE_MAX_DELAY), vars.zSPD[s][p][d]);
               pathExpr.addConstant(Integer.MAX_VALUE);
               pathExpr.addTerm(-Integer.MAX_VALUE, vars.zSPD[s][p][d]);
               GRBLinExpr totalDelayExpr = new GRBLinExpr();
               totalDelayExpr.add(processDelayGlobalExpr); // adds processing delay
               totalDelayExpr.add(linkDelayExpr(s, p)); // adds propagation delay
               if (initialModel != null)
                  totalDelayExpr.add(migrationDelayExpr(initialModel, s, d, p)); // adds migration delay
               String constrName = SD + " [ " + pm.getServices().get(s).getTrafficFlow().getSrc() + " ]"
                       + "[ " + pm.getServices().get(s).getTrafficFlow().getDst() + " ]";
               model.getGrbModel().addConstr(totalDelayExpr, GRB.LESS_EQUAL, pathExpr, constrName);
               model.getGrbModel().addConstr(totalDelayExpr, GRB.EQUAL, vars.dSPD[s][p][d], constrName);
            }
   }

   private GRBLinExpr linkDelayExpr(int s, int p) {
      Path path = pm.getServices().get(s).getTrafficFlow().getPaths().get(p);
      GRBLinExpr linkDelayExpr = new GRBLinExpr();
      double pathDelay = 0.0;
      for (int l = 0; l < path.getEdgePath().size(); l++)
         pathDelay += (double) path.getEdgePath().get(l).getAttribute(LINK_DELAY);
      linkDelayExpr.addTerm(pathDelay, vars.zSP[s][p]);
      return linkDelayExpr;
   }

   private GRBLinExpr processingDelayExpr(int s, int v, int x, int processDelay) {
      GRBLinExpr linExpr = new GRBLinExpr();
      Service service = pm.getServices().get(s);
      double ratio = (double) service.getFunctions().get(v).getAttribute(FUNCTION_LOAD_RATIO)
              * processDelay / (int) service.getFunctions().get(v).getAttribute(FUNCTION_MAX_CAP_SERVER);
      for (int d = 0; d < service.getTrafficFlow().getDemands().size(); d++)
         linExpr.addTerm(ratio * service.getTrafficFlow().getDemands().get(d), vars.fXSVD[x][s][v][d]);
      return linExpr;
   }

   private GRBLinExpr migrationDelayExpr(GRBModel initialModel, int s, int d, int p) throws GRBException {
      Service service = pm.getServices().get(s);
      Path path = service.getTrafficFlow().getPaths().get(p);
      for (int n = 0; n < path.getNodePath().size(); n++)
         for (int x = 0; x < pm.getServers().size(); x++)
            if (pm.getServers().get(x).getParent().equals(path.getNodePath().get(n)))
               for (int v = 0; v < service.getFunctions().size(); v++)
                  if (initialModel.getVarByName(fXSV + "[" + x + "][" + s + "][" + v + "]").get(GRB.DoubleAttr.X) == 1.0) {
                     GRBLinExpr linExpr = new GRBLinExpr();
                     int delay = (int) service.getFunctions().get(v).getAttribute(FUNCTION_MIGRATION_DELAY);
                     linExpr.addTerm(-delay, vars.fXSV[x][s][v]);
                     linExpr.addConstant(delay);
                     model.getGrbModel().addConstr(linExpr, GRB.LESS_EQUAL, vars.mS[s], FUNCTION_MIGRATION_DELAY);
                  }
      GRBLinExpr linExpr = new GRBLinExpr();
      linExpr.addTerm(1.0, vars.mS[s]);
      return linExpr;
   }
}
