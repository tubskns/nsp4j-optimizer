package lp;

import gui.Scenario;
import gurobi.GRB;
import gurobi.GRBModel;
import gurobi.GRBVar;
import output.Parameters;

import static output.Parameters.*;

public class Variables {

   // objective variables
   public GRBVar[] uL; // link utilization
   public GRBVar[] uX; // server utilization
   public GRBVar[] fX; // binary, true if server is used
   public GRBVar[] xN; // integer, num servers per node
   // general variables
   public GRBVar[][] zSP; // binary, routing per path
   public GRBVar[][][] zSPD; // binary, routing per demand
   public GRBVar[][][] fXSV; // binary, placement per server
   public GRBVar[][][][] fXSVD; // binary, placement per demand
   // delay
   public GRBVar[][][] dSVX;// processing delay of a function v in server x
   public GRBVar[] mS; // continuous, migration delay of a service
   public GRBVar[][][][] dSVXD; // continuous, aux variable for processing delay
   public GRBVar[][][] ySVX; //continuous, aux delay
   // synchronization traffic
   public GRBVar[][][][] gSVXY; //binary, aux synchronization traffic
   public GRBVar[][][] hSVP; // binary, traffic synchronization
   // model specific
   GRBVar[] kL; // link cost utilization
   GRBVar[] kX; // server cost utilization
   GRBVar uMax; // max utilization

   public Variables(manager.Parameters pm, GRBModel model, Scenario scenario) {
      try {
         zSP = new GRBVar[pm.getServices().size()][pm.getPathsTrafficFlow()];
         for (int s = 0; s < pm.getServices().size(); s++)
            for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++)
               zSP[s][p] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY
                       , Parameters.zSP + "[" + s + "][" + p + "]");

         zSPD = new GRBVar[pm.getServices().size()][pm.getPathsTrafficFlow()][pm.getDemandsTrafficFlow()];
         for (int s = 0; s < pm.getServices().size(); s++)
            for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++)
               for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++)
                  zSPD[s][p][d] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY
                          , Parameters.zSPD + "[" + s + "][" + p + "][" + d + "]");

         fX = new GRBVar[pm.getServers().size()];
         for (int x = 0; x < pm.getServers().size(); x++)
            this.fX[x] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY
                    , Parameters.fX + "[" + x + "]");

         fXSV = new GRBVar[pm.getServers().size()][pm.getServices().size()][pm.getServiceLength()];
         for (int x = 0; x < pm.getServers().size(); x++)
            for (int s = 0; s < pm.getServices().size(); s++)
               for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
                  fXSV[x][s][v] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY
                          , Parameters.fXSV + "[" + x + "][" + s + "][" + v + "]");

         fXSVD = new GRBVar[pm.getServers().size()][pm.getServices().size()]
                 [pm.getServiceLength()][pm.getDemandsTrafficFlow()];
         for (int x = 0; x < pm.getServers().size(); x++)
            for (int s = 0; s < pm.getServices().size(); s++)
               for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
                  for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++)
                     fXSVD[x][s][v][d] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY
                             , Parameters.fXSVD + "[" + x + "][" + s + "][" + v + "][" + d + "]");

         uL = new GRBVar[pm.getLinks().size()];
         for (int l = 0; l < pm.getLinks().size(); l++)
            uL[l] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS
                    , Parameters.uL + "[" + l + "]");

         uX = new GRBVar[pm.getServers().size()];
         for (int x = 0; x < pm.getServers().size(); x++)
            uX[x] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS
                    , Parameters.uX + "[" + x + "]");

         kL = new GRBVar[pm.getLinks().size()];
         for (int l = 0; l < pm.getLinks().size(); l++)
            kL[l] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS
                    , Parameters.kL + "[" + l + "]");

         kX = new GRBVar[pm.getServers().size()];
         for (int x = 0; x < pm.getServers().size(); x++)
            kX[x] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS
                    , Parameters.kX + "[" + x + "]");

         // if model is optimizing max utilization
         if (scenario.getObjectiveFunction().equals(MAX_UTILIZATION_OBJ)) {
            uMax = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS
                    , Parameters.uMax);
         }
         model.update();
      } catch (Exception ignored) {
      }
   }

   public void initializeAdditionalVariables(manager.Parameters pm, GRBModel model, Scenario scenario) {
      try {
         // if model is dimensioning number of servers
         if (scenario.getObjectiveFunction().equals(SERVER_DIMENSIONING)) {
            xN = new GRBVar[pm.getNodes().size()];
            for (int n = 0; n < pm.getNodes().size(); n++)
               this.xN[n] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER
                       , Parameters.xN + "[" + n + "]");
         }
         // if model is considering synchronization traffic
         if (scenario.getConstraints().get(SYNC_TRAFFIC)) {
            gSVXY = new GRBVar[pm.getServices().size()][pm.getServiceLength()]
                    [pm.getServers().size()][pm.getServers().size()];
            for (int s = 0; s < pm.getServices().size(); s++)
               for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
                  for (int x = 0; x < pm.getServers().size(); x++)
                     for (int y = 0; y < pm.getServers().size(); y++)
                        if (!pm.getServers().get(x).getParent().equals(pm.getServers().get(y).getParent()))
                           gSVXY[s][v][x][y] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY
                                   , Parameters.gSVXY + "[" + s + "][" + v + "][" + x + "][" + y + "]");

            hSVP = new GRBVar[pm.getServices().size()][pm.getServiceLength()][pm.getPaths().size()];
            for (int s = 0; s < pm.getServices().size(); s++)
               for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
                  for (int p = 0; p < pm.getPaths().size(); p++)
                     hSVP[s][v][p] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY
                             , Parameters.hSVP + "[" + s + "][" + v + "][" + p + "]");
         }
         // if model is considering delay constraints
         if (scenario.getConstraints().get(SERV_DELAY)) {
            dSVX = new GRBVar[pm.getServices().size()][pm.getServiceLength()][pm.getServers().size()];
            for (int s = 0; s < pm.getServices().size(); s++)
               for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
                  for (int x = 0; x < pm.getServers().size(); x++)
                     dSVX[s][v][x] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS
                             , Parameters.dSVX + "[" + s + "][" + v + "][" + x + "]");

            dSVXD = new GRBVar[pm.getServices().size()][pm.getServiceLength()][pm.getServers().size()][pm.getDemandsTrafficFlow()];
            for (int s = 0; s < pm.getServices().size(); s++)
               for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
                  for (int x = 0; x < pm.getServers().size(); x++)
                     for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++)
                        dSVXD[s][v][x][d] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS
                                , Parameters.dSVXD + "[" + s + "][" + v + "][" + x + "][" + d + "]");

            mS = new GRBVar[pm.getServices().size()];
            for (int s = 0; s < pm.getServices().size(); s++)
               mS[s] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, Parameters.mS + "[" + s + "]");

            ySVX = new GRBVar[pm.getServices().size()][pm.getServiceLength()][pm.getServers().size()];
            for (int s = 0; s < pm.getServices().size(); s++)
               for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
                  for (int x = 0; x < pm.getServers().size(); x++)
                     ySVX[s][v][x] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS
                             , Parameters.ySVX + "[" + s + "][" + v + "][" + x + "]");
         }
         model.update();
      } catch (Exception ignored) {
      }
   }
}
