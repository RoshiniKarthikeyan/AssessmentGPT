import { useEffect, useState } from 'react';
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import axios from 'axios';
import Editor from '@monaco-editor/react';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, ScatterChart, Scatter } from 'recharts';
import { LayoutDashboard, ClipboardList, Users, BarChart3, FileDown, LogOut, Send, Play, Upload, Sparkles, Clock, Coins, Code2 } from 'lucide-react';

const api = axios.create({ baseURL: 'http://localhost:8080/api' });
const initialCode = `public class Solution {
    public static Integer secondLargest(int[] nums) {
        java.util.TreeSet<Integer> values = new java.util.TreeSet<>();
        for (int value : nums) values.add(value);
        if (values.size() < 2) return null;
        values.pollLast();
        return values.pollLast();
    }
}`;

function Login() {
  const navigate = useNavigate();
  const [username, setUsername] = useState('candidate');
  const [password, setPassword] = useState('candidate123');
  const [error, setError] = useState('');
  const submit = async (e) => {
    e.preventDefault();
    try {
      const { data } = await api.post('/auth/login', { username, password });
      localStorage.setItem('auth', JSON.stringify(data));
      navigate(data.role === 'ADMIN' ? '/admin' : '/candidate');
    } catch (e) { setError(e.response?.data?.message || 'Login failed'); }
  };
  return <div className="login-page"><div className="login-card">
    <div className="brand"><div className="brand-mark"><Code2 /></div><h1>CodeAssess AI</h1></div>
    <p className="muted">Sign in to your assessment workspace</p>
    <form onSubmit={submit}>
      <label>Username<input value={username} onChange={e=>setUsername(e.target.value)} /></label>
      <label>Password<input type="password" value={password} onChange={e=>setPassword(e.target.value)} /></label>
      {error && <div className="error">{error}</div>}
      <button className="primary full" type="submit">Sign in</button>
    </form>
    <div className="demo-creds"><b>Demo credentials</b><span>Admin: admin / admin123</span><span>Candidate: candidate / candidate123</span></div>
  </div></div>;
}

function Shell({ children, role }) {
  const navigate = useNavigate();
  const auth = JSON.parse(localStorage.getItem('auth') || '{}');
  const logout = () => { localStorage.removeItem('auth'); navigate('/login'); };
  const items = role === 'ADMIN'
    ? [['Dashboard','/admin',LayoutDashboard],['Challenges','/admin/challenges',ClipboardList],['Candidates','/admin/candidates',Users],['Analytics','/admin/analytics',BarChart3],['Export','/admin/export',FileDown]]
    : [['Dashboard','/candidate',LayoutDashboard],['Assessment','/workspace',Code2],['Results','/candidate/results',BarChart3]];
  return <div className="app-shell"><aside>
    <div className="brand mini"><div className="brand-mark"><Code2 /></div><b>CodeAssess AI</b></div>
    <nav>{items.map(([label,path,Icon]) => <button key={path} onClick={()=>navigate(path)}><Icon size={18}/>{label}</button>)}</nav>
    <button className="logout" onClick={logout}><LogOut size={18}/>Log out</button>
  </aside><main><header><div><span className="eyebrow">Assessment Platform</span><h2>{role === 'ADMIN' ? 'Admin workspace' : 'Candidate workspace'}</h2></div><div className="avatar">{auth.name?.split(' ').map(n=>n[0]).join('') || 'U'}</div></header><div className="content">{children}</div></main></div>;
}

function Stat({ label, value, icon: Icon, tone }) { return <div className="stat"><div className={`stat-icon ${tone}`}><Icon size={20}/></div><div><span>{label}</span><b>{value}</b></div></div>; }

function AdminDashboard() {
  const [data,setData] = useState(null);
  useEffect(()=>{api.get('/admin/analytics').then(r=>setData(r.data));},[]);
  if(!data) return <div className="loading">Loading analytics...</div>;
  return <Shell role="ADMIN"><div className="page-heading"><div><span className="eyebrow">Overview</span><h1>Administrator Dashboard</h1></div><span className="demo-badge">DEMO DATA</span></div>
    <div className="stats"><Stat label="Candidates" value={data.totalCandidates} icon={Users} tone="blue"/><Stat label="Assessments" value={data.totalAssessments} icon={ClipboardList} tone="purple"/><Stat label="Average score" value={`${data.averageScore}/100`} icon={BarChart3} tone="green"/><Stat label="AI requests" value={data.aiRequestCount} icon={Sparkles} tone="amber"/></div>
    <div className="grid two"><div className="panel"><div className="panel-title"><h3>Score distribution</h3></div><ResponsiveContainer width="100%" height={260}><BarChart data={data.candidates}><XAxis dataKey="name"/><YAxis/><Tooltip/><Bar dataKey="score" fill="#635bff" radius={[6,6,0,0]}/></BarChart></ResponsiveContainer></div>
    <div className="panel"><div className="panel-title"><h3>Score vs token usage</h3></div><ResponsiveContainer width="100%" height={260}><ScatterChart><XAxis type="number" dataKey="tokens" name="tokens"/><YAxis type="number" dataKey="score" name="score"/><Tooltip/><Scatter data={data.candidates} fill="#20b486"/></ScatterChart></ResponsiveContainer></div></div>
  </Shell>;
}

function Challenges() {
  const [challenges,setChallenges]=useState([]);
  const [show,setShow]=useState(false);
  const [form,setForm]=useState({title:'',difficulty:'Medium',durationMinutes:60,tokenBudget:12000,problem:''});
  useEffect(()=>{api.get('/challenges').then(r=>setChallenges(r.data));},[]);
  const save=async()=>{const r=await api.post('/admin/challenges',form); setChallenges(v=>[...v,r.data]); setShow(false);};
  return <Shell role="ADMIN"><div className="page-heading"><div><span className="eyebrow">Content</span><h1>Challenges</h1></div><button className="primary" onClick={()=>setShow(!show)}>Create challenge</button></div>
    {show&&<div className="panel form-panel"><div className="grid two"><label>Title<input value={form.title} onChange={e=>setForm({...form,title:e.target.value})}/></label><label>Difficulty<select value={form.difficulty} onChange={e=>setForm({...form,difficulty:e.target.value})}><option>Easy</option><option>Medium</option><option>Hard</option></select></label></div><label>Problem statement<textarea value={form.problem} onChange={e=>setForm({...form,problem:e.target.value})}/></label><button className="primary" onClick={save}>Save challenge</button></div>}
    <div className="panel"><table><thead><tr><th>Challenge</th><th>Difficulty</th><th>Duration</th><th>Token budget</th><th>Status</th></tr></thead><tbody>{challenges.map(c=><tr key={c.id}><td><b>{c.title}</b></td><td><span className={`pill ${String(c.difficulty).toLowerCase()}`}>{c.difficulty}</span></td><td>{c.durationMinutes} min</td><td>{c.tokenBudget}</td><td><span className="pill published">{c.published?'Published':'Draft'}</span></td></tr>)}</tbody></table></div>
  </Shell>;
}

function CandidateDashboard() {
  const navigate=useNavigate();
  return <Shell role="CANDIDATE"><div className="page-heading"><div><span className="eyebrow">My assessments</span><h1>Welcome back, Alex</h1></div></div><div className="assessment-card"><div className="assessment-icon"><Code2/></div><div className="grow"><span className="pill medium">Medium</span><h2>Find the Second Largest Unique Number</h2><p>Java · 60 minutes · 12,000 token budget</p></div><button className="primary" onClick={async()=>{const r=await api.post('/assessments/start',{challengeId:'1'}); localStorage.setItem('assessmentId',r.data.id); navigate('/workspace');}}>Start assessment</button></div></Shell>;
}

function Workspace() {
  const [challenge,setChallenge]=useState(null);
  const [code,setCode]=useState(initialCode);
  const [prompt,setPrompt]=useState('');
  const [messages,setMessages]=useState([]);
  const [output,setOutput]=useState(null);
  const [tokens,setTokens]=useState({totalTokens:0,budget:12000,requests:0,inputTokens:0,outputTokens:0});
  const [seconds,setSeconds]=useState(3600);
  const [buggy,setBuggy]=useState(false);
  const assessmentId=localStorage.getItem('assessmentId')||'demo';
  useEffect(()=>{api.get('/challenges/1').then(r=>setChallenge(r.data)); api.get(`/tokens/${assessmentId}/analytics`).then(r=>setTokens(r.data.usage)); const timer=setInterval(()=>setSeconds(s=>Math.max(0,s-1)),1000); return()=>clearInterval(timer);},[assessmentId]);
  const send=async()=>{if(!prompt.trim())return; const r=await api.post('/ai/refine',{assessmentId,prompt,code,action:'generate'}); setMessages(m=>[...m,{role:'user',text:prompt},{role:'assistant',text:r.data.content,mode:r.data.mode}]); setPrompt(''); setTokens(r.data.usage);};
  const run=async()=>{const r=await api.post('/execution/run',{assessmentId,code}); setOutput(r.data);};
  const inject=async()=>{const r=await api.post('/debug/inject',{assessmentId,code}); setCode(r.data.buggyCode); setBuggy(true); setOutput({passed:false,output:r.data.message,mode:'BUG INJECTION',safeNote:'Bug injection ID: '+r.data.bugInjectionId,tests:[{name:'debug-hidden-1',passed:false}]});};
  const resubmit=async()=>{const r=await api.post('/debug/resubmit',{assessmentId,code}); setOutput({passed:r.data.passed,output:r.data.message,mode:'DEBUG SUBMISSION',tests:[{name:'debug-hidden-suite',passed:r.data.passed}]});};
  const submit=async()=>{const r=await api.post(`/assessments/${assessmentId}/submit`,{fixed:!output||output.passed?true:false}); localStorage.setItem('score',JSON.stringify(r.data));};
  if(!challenge)return <div className="loading">Loading workspace...</div>;
  const pct=Math.min(100,Math.round(tokens.totalTokens/tokens.budget*100));
  return <div className="workspace"><div className="workspace-top"><div className="brand mini"><div className="brand-mark"><Code2/></div><b>CodeAssess AI</b></div><div className="timer"><Clock size={16}/>{String(Math.floor(seconds/3600)).padStart(2,'0')}:{String(Math.floor(seconds%3600/60)).padStart(2,'0')}:{String(seconds%60).padStart(2,'0')}</div><button className="submit" onClick={submit}><Upload size={16}/>Submit</button></div>
  <div className="ide"><aside className="problem-pane"><h2>{challenge.title}</h2><span className="pill medium">{challenge.difficulty}</span><h3>Problem</h3><p>{challenge.problem}</p><h3>Requirements</h3><ul>{challenge.requirements?.map((x,i)=><li key={i}>{x}</li>)}</ul><h3>Constraints</h3><code>{challenge.constraints}</code><h3>Public examples</h3>{challenge.publicTests?.map((t,i)=><div className="example" key={i}><span>Input: {t.input}</span><span>Output: {t.output}</span></div>)}</aside>
  <section className="code-pane"><div className="editor-toolbar"><select defaultValue="java"><option value="java">Java 17</option></select><div><button onClick={send}><Sparkles size={15}/>Generate</button><button onClick={run}><Play size={15}/>Run</button><button onClick={buggy?resubmit:inject}>{buggy?'Resubmit fix':'Debug stage'}</button></div></div><Editor height="calc(100% - 42px)" defaultLanguage="java" theme="vs-dark" value={code} onChange={v=>setCode(v||'')} options={{fontSize:14,minimap:{enabled:false}}}/></section>
  <aside className="chat-pane"><div className="chat-header"><h3>AI Assistant</h3><span className="demo-badge">DEMO/OPENAI</span></div><div className="messages">{messages.length===0&&<div className="empty-chat"><Sparkles/><p>Ask for an implementation or explain an error.</p></div>} {messages.map((m,i)=><div key={i} className={`message ${m.role}`}>{m.role==='assistant'&&<span className="mode">{m.mode}</span>}<pre>{m.text}</pre></div>)}</div><div className="token-card"><div><b><Coins size={15}/> Token budget</b><span>{pct}% used</span></div><div className="meter"><i style={{width:`${pct}%`}}/></div><div className="token-meta"><span>{tokens.totalTokens} / {tokens.budget}</span><span>{tokens.requests} requests</span></div></div><div className="chat-input"><input value={prompt} onChange={e=>setPrompt(e.target.value)} onKeyDown={e=>e.key==='Enter'&&send()} placeholder="Refine the solution..."/><button onClick={send}><Send size={17}/></button></div></aside>
  <div className="output-pane"><div className="output-title"><h3>Execution output</h3>{output&&<span className={output.passed?'success':'failure'}>{output.passed?'Passed':'Needs attention'}</span>}</div>{output?<div className="output-content"><b>{output.mode}</b><p>{output.output}</p><small>{output.safeNote}</small>{output.tests?.map((t,i)=><div className="test-row" key={i}><span>{t.name}</span><b className={t.passed?'success':'failure'}>{t.passed?'Passed':'Failed'}</b></div>)}</div>:<p className="muted">Run code to see compilation and test feedback.</p>}</div></div></div>;
}

function CandidateResults() {
  const navigate=useNavigate();
  const score=JSON.parse(localStorage.getItem('score')||'null');
  return <Shell role="CANDIDATE"><div className="page-heading"><h1>Candidate Results</h1></div><div className="scorecard">{score?<><div className="score-ring">{score.total}<small>/100</small></div><div className="grid four">{Object.entries(score.breakdown).map(([k,v])=><div className="score-item" key={k}><span>{k}</span><b>{v.score}/{v.max}</b><small>{v.reason}</small></div>)}</div></>:<><p>No submission score yet.</p><button className="primary" onClick={()=>navigate('/workspace')}>Open workspace</button></>}</div></Shell>;
}

function CandidatesPage() {
  const [rows,setRows]=useState([]);
  useEffect(()=>api.get('/admin/candidates').then(r=>setRows(r.data)),[]);
  return <Shell role="ADMIN"><div className="page-heading"><h1>Candidates</h1><span className="demo-badge">DEMO DATA</span></div><div className="panel"><table><thead><tr><th>Candidate</th><th>ID</th><th>Score</th><th>Tokens</th><th>Status</th></tr></thead><tbody>{rows.map(r=><tr key={r.id}><td><b>{r.name}</b></td><td>{r.id}</td><td>{r.score}/100</td><td>{r.tokens}</td><td><span className="pill published">Evaluated</span></td></tr>)}</tbody></table></div></Shell>;
}

function ExportPage() {
  const download=async()=>{const r=await api.get('/admin/export/excel',{responseType:'blob'}); const url=URL.createObjectURL(r.data); const a=document.createElement('a'); a.href=url;a.download='assessment-report.xlsx';a.click();URL.revokeObjectURL(url);};
  return <Shell role="ADMIN"><div className="page-heading"><h1>Excel Export</h1></div><div className="export-card"><FileDown size={32}/><h2>Assessment report</h2><p>Includes candidate summaries, score details, token analytics, prompts, and test results.</p><button className="primary" onClick={download}>Download Excel workbook</button></div></Shell>;
}

function Protected({role,children}) {
  const auth=JSON.parse(localStorage.getItem('auth')||'{}');
  return auth.role===role?children:<Navigate to="/login"/>;
}

export default function App(){
  return <Routes><Route path="/login" element={<Login/>}/><Route path="/" element={<Navigate to="/login"/>}/>
    <Route path="/admin" element={<Protected role="ADMIN"><AdminDashboard/></Protected>}/>
    <Route path="/admin/challenges" element={<Protected role="ADMIN"><Challenges/></Protected>}/>
    <Route path="/admin/candidates" element={<Protected role="ADMIN"><CandidatesPage/></Protected>}/>
    <Route path="/admin/analytics" element={<Protected role="ADMIN"><AdminDashboard/></Protected>}/>
    <Route path="/admin/export" element={<Protected role="ADMIN"><ExportPage/></Protected>}/>
    <Route path="/candidate" element={<Protected role="CANDIDATE"><CandidateDashboard/></Protected>}/>
    <Route path="/workspace" element={<Protected role="CANDIDATE"><Workspace/></Protected>}/>
    <Route path="/candidate/results" element={<Protected role="CANDIDATE"><CandidateResults/></Protected>}/>
  </Routes>;
}
