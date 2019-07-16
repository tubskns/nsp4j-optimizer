package lp;

import gurobi.*;
import manager.Manager;
import manager.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import output.Auxiliary;

import static output.Auxiliary.*;
import static output.Definitions.*;

public class OptimizationModel {

   private static final Logger log = LoggerFactory.getLogger(OptimizationModel.class);
   private GRBModel grbModel;
   private GRBEnv grbEnv;
   private Variables variables;
   private Parameters parameters;
   private double objVal;

   public OptimizationModel(Parameters parameters) {
      this.parameters = parameters;
      try {
         grbEnv = new GRBEnv();
         grbEnv.set(GRB.IntParam.LogToConsole, 0);
         grbModel = new GRBModel(grbEnv);
         Callback cb = new Callback();
         grbModel.setCallback(cb);
         grbModel.getEnv().set(GRB.DoubleParam.MIPGap, (double) parameters.getAux().get("gap"));
      } catch (GRBException e) {
         e.printStackTrace();
      }
   }

   public void setObjectiveFunction(GRBLinExpr expr, boolean isMaximization) throws GRBException {
      if (!isMaximization)
         grbModel.setObjective(expr, GRB.MINIMIZE);
      else
         grbModel.setObjective(expr, GRB.MAXIMIZE);
   }

   public GRBLinExpr dimensioningExpr() {
      GRBLinExpr expr = new GRBLinExpr();
      for (int n = 0; n < parameters.getNodes().size(); n++)
         expr.addTerm(1.0, variables.xN[n]);
      return expr;
   }

   public GRBLinExpr usedServersExpr() {
      GRBLinExpr expr = new GRBLinExpr();
      for (int x = 0; x < parameters.getServers().size(); x++)
         expr.addTerm(1.0, variables.fX[x]);
      return expr;
   }

   public GRBLinExpr linkCostsExpr(double weight) {
      GRBLinExpr expr = new GRBLinExpr();
      for (int l = 0; l < parameters.getLinks().size(); l++)
         expr.addTerm(weight, variables.kL[l]);
      return expr;
   }

   public GRBLinExpr serverCostsExpr(double weight) {
      GRBLinExpr expr = new GRBLinExpr();
      for (int x = 0; x < parameters.getServers().size(); x++)
         expr.addTerm(weight, variables.kX[x]);
      return expr;
   }

   public GRBLinExpr linkUtilizationExpr(double weight) {
      GRBLinExpr expr = new GRBLinExpr();
      for (int l = 0; l < parameters.getLinks().size(); l++)
         expr.addTerm(weight, variables.uL[l]);
      return expr;
   }

   public GRBLinExpr serverUtilizationExpr(double weight) {
      GRBLinExpr expr = new GRBLinExpr();
      for (int x = 0; x < parameters.getServers().size(); x++)
         expr.addTerm(weight, variables.uX[x]);
      return expr;
   }

   public GRBLinExpr maxUtilizationExpr(double weight) {
      GRBLinExpr expr = new GRBLinExpr();
      expr.addTerm(weight, variables.uMax);
      return expr;
   }

   public double run() throws GRBException {
      grbModel.optimize();
      if (grbModel.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {
         objVal = Auxiliary.roundDouble(grbModel.get(GRB.DoubleAttr.ObjVal), 2);
         printLog(log, INFO, "finished [" + objVal + "]");
         return objVal;
      } else if (grbModel.get(GRB.IntAttr.Status) == GRB.Status.INFEASIBLE) {
         grbModel.computeIIS();
         printISS();
         printLog(log, ERROR, "model is infeasible");
      } else if (grbModel.get(GRB.IntAttr.Status) == GRB.Status.INF_OR_UNBD)
         printLog(log, ERROR, "solution is inf. or unbd.");
      else if (grbModel.get(GRB.IntAttr.Status) == GRB.Status.INTERRUPTED)
         printLog(log, INFO, "optimization interrupted");
      else
         printLog(log, ERROR, "no solution [" + grbModel.get(GRB.IntAttr.Status) + "]");
      return -1;
   }

   private void printISS() throws GRBException {
      printLog(log, INFO, "constraints in IIS: ");
      for (GRBConstr constr : grbModel.getConstrs())
         if (constr.get(GRB.IntAttr.IISConstr) > 0)
            printLog(log, INFO, constr.get(GRB.StringAttr.ConstrName));
      printLog(log, INFO, "variables in IIS: ");
      for (GRBVar var : grbModel.getVars())
         if (var.get(GRB.IntAttr.IISLB) > 0 || var.get(GRB.IntAttr.IISUB) > 0)
            printLog(log, INFO, var.get(GRB.StringAttr.VarName));
   }

   public GRBModel getGrbModel() {
      return grbModel;
   }

   public Variables getVariables() {
      return variables;
   }

   public void setVariables(Variables variables) {
      this.variables = variables;
   }

   public double getObjVal() {
      return objVal;
   }

   private class Callback extends GRBCallback {

      private boolean isPresolving = false;
      private double gap;

      Callback() {
      }

      @Override
      protected void callback() {
         try {
            if (where == GRB.CB_POLLING) {
               // Ignore polling callback
            } else if (where == GRB.CB_PRESOLVE && !isPresolving) {
               printLog(log, INFO, "pre-solving model");
               isPresolving = true;
            } else if (where == GRB.CB_MIPNODE) {
               double objbst = Auxiliary.roundDouble(getDoubleInfo(GRB.CB_MIPNODE_OBJBST), 2);
               double objbnd = Auxiliary.roundDouble(getDoubleInfo(GRB.CB_MIPNODE_OBJBND), 2);
               double numerator = Math.abs(objbnd - objbst);
               double denominator = Math.abs(objbst);
               double newGap = Auxiliary.roundDouble((numerator / denominator) * 100, 2);
               if (newGap != gap) {
                  gap = newGap;
                  printLog(log, INFO, "[" + objbst + "-" + objbnd + "][" + gap + "%]");
               }
            }
            if (Manager.isInterrupted())
               abort();
         } catch (GRBException e) {
            e.printStackTrace();
         }
      }
   }
}
