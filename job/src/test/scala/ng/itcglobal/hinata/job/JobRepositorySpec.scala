package ng.itcglobal.hinata
package job

import org.scalatest.wordspec.AnyWordSpecLike

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit

import ng.itcglobal.hinata._
import job.JobRepository

import JobRepository._ 

class JobRepositorySpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

   val jobRepo = testKit.spawn( JobRepository(), "jobRepo")
   val probe = testKit.createTestProbe[JobRepository.Response]()

   val jobPost = JobRepository.Job(
        id          = 1L,
        projectName = "hinata",
        status      = Failed,
        duration    = 2L 
        )

  "the JobRepository" must {
    "add job to repo" in {
       jobRepo ! JobRepository.AddJob(jobPost, probe.ref)
       probe.expectMessage(JobRepository.OK)

    }

    "should not allow same id for job" ignore {
      
    }

    "get the job by using id" in {
      val probe = testKit.createTestProbe[Option[Job]]()
      jobRepo ! GetJobById(1L,probe.ref)
      probe.expectMessage(Some(jobPost))
    }
  }
}