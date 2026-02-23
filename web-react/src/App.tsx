import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import Workbench from "@/pages/Workbench";
import Agents from "@/pages/Agents";
import AgentDetail from "@/pages/AgentDetail";
import LocalDev from "@/pages/LocalDev";
import SkillsStudio from "@/pages/SkillsStudio";
import ChatHome from "@/pages/ChatHome";
import ChatAgent from "@/pages/ChatAgent";
import RagDebug from "@/pages/RagDebug";

export default function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<Workbench />} />
        <Route path="/agents" element={<Agents />} />
        <Route path="/agents/new" element={<AgentDetail mode="create" />} />
        <Route path="/agents/:agentId" element={<AgentDetail mode="edit" />} />
        <Route path="/skills" element={<SkillsStudio />} />
        <Route path="/chat" element={<ChatHome />} />
        <Route path="/chat/:agentId" element={<ChatAgent />} />
        <Route path="/rag" element={<RagDebug />} />
        <Route path="/local-dev" element={<LocalDev />} />
      </Routes>
    </Router>
  );
}
