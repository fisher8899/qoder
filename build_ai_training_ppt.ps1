$ErrorActionPreference = 'Stop'

$desktop = [Environment]::GetFolderPath('Desktop')
$outFile = Join-Path $desktop 'AI_Coding_Training_Complete_2026.pptx'

$pp = New-Object -ComObject PowerPoint.Application
$pp.Visible = -1
$pres = $pp.Presentations.Add()

function Rgb([int]$r,[int]$g,[int]$b){ return ($r + 256*$g + 65536*$b) }

function Set-Bg($slide, $r, $g, $b) {
  $slide.FollowMasterBackground = 0
  $fill = $slide.Background.Fill
  $fill.Visible = -1
  $fill.Solid()
  $fill.ForeColor.RGB = (Rgb $r $g $b)
}

function Add-Accent($slide){
  $top = $slide.Shapes.AddShape(1, 0, 0, 1280, 16)
  $top.Fill.ForeColor.RGB = (Rgb 37 167 245)
  $top.Line.Visible = 0
  $bot = $slide.Shapes.AddShape(1, 0, 704, 1280, 16)
  $bot.Fill.ForeColor.RGB = (Rgb 28 114 199)
  $bot.Line.Visible = 0
}

function Add-Title($slide, $title, $subtitle){
  $t = $slide.Shapes.AddTextbox(1, 48, 34, 1180, 88).TextFrame.TextRange
  $t.Text = $title
  $t.Font.Name = 'Segoe UI'
  $t.Font.Size = 36
  $t.Font.Bold = -1
  $t.Font.Color.RGB = (Rgb 247 247 247)
  if($subtitle){
    $s = $slide.Shapes.AddTextbox(1, 52, 120, 1120, 56).TextFrame.TextRange
    $s.Text = $subtitle
    $s.Font.Name = 'Segoe UI'
    $s.Font.Size = 18
    $s.Font.Color.RGB = (Rgb 205 221 236)
  }
}

function Add-Bullets($slide, $items){
  $tr = $slide.Shapes.AddTextbox(1, 78, 196, 1118, 458).TextFrame.TextRange
  $tr.Text = ($items -join "`r")
  $tr.Font.Name = 'Segoe UI'
  $tr.Font.Size = 23
  $tr.Font.Color.RGB = (Rgb 238 238 238)
  $tr.ParagraphFormat.Bullet.Visible = -1
  $tr.ParagraphFormat.Bullet.Character = 8226
  $tr.ParagraphFormat.SpaceAfter = 11
}

function Add-Footer($slide, $text){
  $f = $slide.Shapes.AddTextbox(1, 48, 666, 1180, 28).TextFrame.TextRange
  $f.Text = $text
  $f.Font.Name = 'Segoe UI'
  $f.Font.Size = 12
  $f.Font.Color.RGB = (Rgb 170 188 205)
}

function Add-Quote($slide, $text){
  $q = $slide.Shapes.AddShape(1, 78, 574, 940, 72)
  $q.Fill.ForeColor.RGB = (Rgb 19 122 207)
  $q.Line.Visible = 0
  $qt = $q.TextFrame.TextRange
  $qt.Text = $text
  $qt.Font.Name = 'Segoe UI'
  $qt.Font.Size = 19
  $qt.Font.Bold = -1
  $qt.Font.Color.RGB = (Rgb 255 255 255)
}

# cover
$s = $pres.Slides.Add(1,12)
Set-Bg $s 14 29 45
Add-Accent $s
Add-Title $s 'AI Coding: From Assistance to End-to-End Delivery' 'Internal Enablement Deck | Software Custom Development Team'
$tag = $s.Shapes.AddShape(1, 52, 240, 860, 52)
$tag.Fill.ForeColor.RGB = (Rgb 23 128 212)
$tag.Line.Visible = 0
$tt = $tag.TextFrame.TextRange
$tt.Text = 'Objective: Improve delivery speed under strict quality and control'
$tt.Font.Name='Segoe UI'; $tt.Font.Size=20; $tt.Font.Color.RGB=(Rgb 255 255 255)
Add-Footer $s '2026 | Speaker: AI Engineering Practice'

$slides = @(
@{T='Agenda'; S='What this session covers'; F='Complete deck: 16 slides'; B=@(
 '1) Why AI coding changed in 2026',
 '2) What end-to-end delivery really means',
 '3) A concrete daily workflow example',
 '4) Capability boundaries and common pitfalls',
 '5) Tools, models, governance, and 90-day rollout'
)},
@{T='1. Why Re-evaluate AI Coding Now'; S='Management context and technology shift'; F='From helper to executor'; B=@(
 'Past perception: AI helps write snippets and local modules only.',
 'Current reality: AI agents can read repos, patch files, run tests, and prepare PRs.',
 'Business impact: less cycle time for iterative custom development.',
 'Decision focus: controlled adoption, not blind automation.'
)},
@{T='2. What End-to-End Delivery Means'; S='Practical definition for engineering managers'; F='Human remains accountable'; B=@(
 'Input: requirement, constraints, acceptance criteria, and scope boundaries.',
 'Execution: implementation across backend, frontend, and integration points.',
 'Verification: build, tests, static checks, and targeted regressions.',
 'Output: review-ready change set with risk notes and release checklist.'
)},
@{T='3. Capability Map in 2026'; S='Where AI is strongest in software services'; F='Use strengths intentionally'; B=@(
 'Strong: repetitive coding, refactor scaffolding, test drafting, documentation.',
 'Medium: multi-file feature changes when requirements are explicit.',
 'Weak: unclear domain rules and undocumented cross-system dependencies.',
 'Rule: use AI for execution depth, engineers for business correctness.'
)},
@{T='4. Custom-Dev Example Scenario'; S='Representative requirement from enterprise projects'; F='Realistic workflow'; B=@(
 'Case: multi-role approval flow with department-scoped notifications.',
 'Need: UI behavior consistency, process rules, and traceable status transitions.',
 'AI role: analyze existing code, patch minimal files, verify with build/tests.',
 'Result: faster closure with clearer audit trail and lower coordination overhead.'
)},
@{T='5. Day-to-Day SOP (Reusable)'; S='A standard process your team can adopt'; F='AI executes; engineers decide'; B=@(
 'Step 1: define scope, acceptance checks, and no-touch areas.',
 'Step 2: force repo analysis before coding.',
 'Step 3: implement in small verifiable increments.',
 'Step 4: run build/test after each increment.',
 'Step 5: human review for semantics, risk, and release safety.'
)},
@{T='6. Prompting Pattern That Works'; S='How to ask AI for production-safe output'; F='Prompt quality drives output quality'; B=@(
 'State business goal + technical scope + explicit non-goals.',
 'Require file-level ownership and expected test commands.',
 'Require fallback behavior when assumptions are missing.',
 'Require concise change summary with risk points.'
)},
@{T='7. Boundaries and Failure Modes'; S='Why AI is not magic'; F='Set correct expectations'; B=@(
 'Failure mode A: plausible but wrong business logic.',
 'Failure mode B: hidden side effects across shared modules.',
 'Failure mode C: overconfident conclusions with limited context.',
 'Mitigation: hard acceptance tests + targeted review checklists.'
)},
@{T='8. Personal Pitfalls and Lessons'; S='Practice-based guidance'; F='Convert mistakes into standards'; B=@(
 'Pitfall 1: oversized tasks created large noisy diffs.',
 'Pitfall 2: test pass but requirement mismatch.',
 'Pitfall 3: missing context led to incorrect assumptions.',
 'Pitfall 4: skipping quality gates increased rework.'
)},
@{T='9. Tool Stack Recommendation'; S='Choose by task type, not by hype'; F='Portfolio mindset'; B=@(
 'IDE copilots for high-frequency coding loops.',
 'Terminal agents for deep repo operations and workflow tasks.',
 'Repo agents for issue-to-PR asynchronous collaboration.',
 'Shared templates to stabilize team output quality.'
)},
@{T='10. Model Selection Strategy'; S='Cost, speed, and reasoning tradeoff'; F='Model routing matters'; B=@(
 'Complex architecture/refactor: high-reasoning frontier models.',
 'Routine CRUD and adapters: balanced cost-performance models.',
 'Docs/test generation: lightweight economical models.',
 'Always log model choice with expected quality bar.'
)},
@{T='11. Governance and Risk Control'; S='Address management concerns directly'; F='Control is a design choice'; B=@(
 'Access: sandbox-first, explicit approval for sensitive actions.',
 'Process: branch isolation, mandatory PR review, no direct main push.',
 'Quality: CI gates for build/tests/security/static checks.',
 'Audit: prompts, diffs, owners, and release records are traceable.'
)},
@{T='12. KPI Framework'; S='Measure adoption with business outcomes'; F='Track impact objectively'; B=@(
 'Delivery cycle time (requirement to release).',
 'Defect leakage rate and rollback frequency.',
 'Rework ratio across sprints.',
 'Throughput per engineer and review load balance.'
)},
@{T='13. 90-Day Rollout Plan'; S='Pragmatic path for stable adoption'; F='Pilot first, scale with evidence'; B=@(
 'Weeks 1-2: pilot in 2 projects, set SOP and baseline metrics.',
 'Weeks 3-6: expand to 3-5 teams, unify prompt and review templates.',
 'Weeks 7-12: integrate with CI/CD governance and internal training.',
 'Exit criteria: KPI improvement + stable quality gates.'
)},
@{T='14. Team Onboarding Plan'; S='How developers get productive quickly'; F='Lower ramp-up friction'; B=@(
 'Day 1: tool setup and safety rules.',
 'Week 1: guided tasks with mentor review.',
 'Week 2-4: independent tasks with checklist-based QA.',
 'Month 2+: role-based specialization and internal playbooks.'
)},
@{T='15. Final Message and Q&A'; S='Balanced conclusion'; F='Close with clarity'; B=@(
 'AI coding is now a practical delivery capability, not only a helper.',
 'Value comes from process discipline and governance, not model novelty alone.',
 'Start with controlled pilots, verify with KPI, then scale organization-wide.',
 'Q&A: concerns, risks, and rollout decisions.'
)}
)

$i = 2
foreach($cfg in $slides){
  $s = $pres.Slides.Add($i,12)
  $br = 14 + ($i % 4) * 4
  $bg = 34 + ($i % 5) * 7
  $bb = 52 + ($i % 6) * 7
  Set-Bg $s $br $bg $bb
  Add-Accent $s
  Add-Title $s $cfg.T $cfg.S
  Add-Bullets $s $cfg.B
  Add-Footer $s $cfg.F
  if($i -eq 4 -or $i -eq 8 -or $i -eq 13){
    Add-Quote $s 'Controlled adoption beats uncontrolled speed.'
  }
  $i++
}

$pres.SaveAs($outFile)
$pres.Close()
$pp.Quit()
[System.Runtime.InteropServices.Marshal]::ReleaseComObject($pres) | Out-Null
[System.Runtime.InteropServices.Marshal]::ReleaseComObject($pp) | Out-Null
[GC]::Collect()
[GC]::WaitForPendingFinalizers()

Write-Output $outFile
